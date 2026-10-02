package com.consoleadda.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Helpers for customers, rentals, daily closing and reports. */
public abstract class Phase4TestSupport extends FoodTestSupport {

    protected static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    protected JdbcTemplate jdbc;

    /**
     * Moves the test clock to noon on a random far-future business day that no other test uses, so closing a
     * day or checking a day's totals is unaffected by other tests sharing the database. Get tokens first.
     */
    protected LocalDate moveToFreshBusinessDay() {
        int days = ThreadLocalRandom.current().nextInt(1_000, 2_000_000);
        ZonedDateTime target = clock.instant().atZone(IST).toLocalDate().plusDays(days).atTime(12, 0).atZone(IST);
        clock.advance(Duration.between(clock.instant(), target.toInstant()));
        return target.toLocalDate();
    }

    protected static String uniqueMobile() {
        return "9" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }

    protected long createGame(String name, String purchasePrice, String rentalPrice, int periodDays, String lateFee,
                              String deposit) throws Exception {
        Map<String, Object> game = new HashMap<>();
        game.put("name", name + " " + UUID.randomUUID().toString().substring(0, 8));
        game.put("copyNumber", 1);
        game.put("purchasePrice", purchasePrice);
        game.put("rentalPrice", rentalPrice);
        game.put("rentalPeriodDays", periodDays);
        game.put("lateFeePerDay", lateFee);
        game.put("defaultDeposit", deposit);
        game.put("availableForRental", true);
        game.put("active", true);
        String body = mockMvc.perform(post("/api/games").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(game)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return readJson(body).get("id").asLong();
    }

    protected ResultActions rentRequest(String token, long gameId, String mobile, Integer days, String deposit,
                                        String mode) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("gameId", gameId);
        req.put("customerName", "Renter " + mobile);
        req.put("customerMobile", mobile);
        if (days != null) {
            req.put("rentalDays", days);
        }
        if (deposit != null) {
            req.put("depositAmount", deposit);
        }
        req.put("paymentMode", mode);
        return mockMvc.perform(post("/api/rentals").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(req)));
    }

    protected JsonNode rent(String token, long gameId, String mobile, Integer days, String deposit, String mode)
            throws Exception {
        return readJson(rentRequest(token, gameId, mobile, days, deposit, mode)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    protected ResultActions returnRequest(String token, long rentalId, String mode) throws Exception {
        var builder = post("/api/rentals/{id}/return", rentalId).header("Authorization", bearer(token));
        if (mode != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json(Map.of("paymentMode", mode)));
        }
        return mockMvc.perform(builder);
    }

    protected ResultActions walkInRequest(String token, long foodItemId, int quantity, String mode, String mobile)
            throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("paymentMode", mode);
        req.put("items", List.of(Map.of("foodItemId", foodItemId, "quantity", quantity)));
        if (mobile != null) {
            req.put("customerMobile", mobile);
            req.put("customerName", "Walk-in " + mobile);
        }
        return mockMvc.perform(post("/api/bills").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(req)));
    }

    protected JsonNode walkIn(String token, long foodItemId, int quantity, String mode, String mobile) throws Exception {
        return readJson(walkInRequest(token, foodItemId, quantity, mode, mobile)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    protected static List<MvcResult> race(List<Callable<MvcResult>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> task : tasks) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return task.call();
                }));
            }
            go.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    protected static long count(List<MvcResult> results, int status) {
        return results.stream().filter(r -> r.getResponse().getStatus() == status).count();
    }
}
