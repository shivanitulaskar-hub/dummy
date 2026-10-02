package com.consoleadda.session;

import com.consoleadda.support.SessionTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simulates several counter operators clicking at the same moment. All requests are released together by a
 * latch so they genuinely race inside MySQL.
 */
class SessionConcurrencyIntegrationTest extends SessionTestSupport {

    private static final int THREADS = 8;

    @Autowired
    private JdbcTemplate jdbc;

    private List<MvcResult> race(List<Callable<MvcResult>> tasks) throws Exception {
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

    private static long count(List<MvcResult> results, int status) {
        return results.stream().filter(r -> r.getResponse().getStatus() == status).count();
    }

    @Test
    void simultaneousStartsOnSamePs5CreateExactlyOneSession() throws Exception {
        long station = createStation();
        String op = operatorToken();
        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> startRequest(op, station, 1, null, null).andReturn());
        }
        List<MvcResult> results = race(tasks);

        assertThat(count(results, 201)).as("successful starts").isEqualTo(1);
        assertThat(count(results, 409)).as("rejected as STATION_OCCUPIED").isEqualTo(THREADS - 1);
        results.stream().filter(r -> r.getResponse().getStatus() == 409).forEach(r ->
                assertThat(content(r)).contains("STATION_OCCUPIED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gaming_sessions WHERE station_id = ? AND status = 'ACTIVE'",
                Integer.class, station)).isEqualTo(1);
        // No orphan OPEN bills from the losing requests (their transactions rolled back).
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gaming_sessions WHERE station_id = ?", Integer.class, station))
                .isEqualTo(1);
    }

    @Test
    void simultaneousCompletionsBillExactlyOnce() throws Exception {
        String op = operatorToken();
        long id = start(op, createStation(), 1).get("id").asLong();
        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> completeRequest(op, id, "CASH", null).andReturn());
        }
        List<MvcResult> results = race(tasks);

        assertThat(count(results, 200)).isEqualTo(1);
        assertThat(count(results, 409)).isEqualTo(THREADS - 1);
        Long billId = jdbc.queryForObject("SELECT bill_id FROM gaming_sessions WHERE id = ?", Long.class, id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE bill_id = ?", Integer.class, billId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill_items WHERE bill_id = ?", Integer.class, billId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT bill_number FROM bills WHERE id = ?", String.class, billId)).isNotNull();
    }

    @Test
    void retriedCompletionWithSameKeyReturnsTheSameBill() throws Exception {
        String op = operatorToken();
        long id = start(op, createStation(), 1).get("id").asLong();
        String key = "double-click-" + id;
        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> completeRequest(op, id, "UPI", key).andReturn());
        }
        List<MvcResult> results = race(tasks);

        assertThat(count(results, 200)).as("every retry gets the original result").isEqualTo(4);
        Set<String> billNumbers = new HashSet<>();
        for (MvcResult r : results) {
            billNumbers.add(readJson(content(r)).get("billNumber").asText());
        }
        assertThat(billNumbers).hasSize(1);
        Long billId = jdbc.queryForObject("SELECT bill_id FROM gaming_sessions WHERE id = ?", Long.class, id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE bill_id = ?", Integer.class, billId)).isEqualTo(1);
    }

    @Test
    void parallelBillsOnDifferentStationsGetUniqueConsecutiveNumbers() throws Exception {
        String op = operatorToken();
        List<Long> sessions = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            sessions.add(start(op, createStation(), 1).get("id").asLong());
        }
        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (long id : sessions) {
            tasks.add(() -> completeRequest(op, id, "CASH", null).andReturn());
        }
        List<MvcResult> results = race(tasks);

        assertThat(count(results, 200)).as("statuses: %s", results.stream()
                .map(r -> r.getResponse().getStatus() + " " + content(r)).toList()).isEqualTo(THREADS);
        List<Long> numbers = new ArrayList<>();
        for (MvcResult r : results) {
            String bn = readJson(content(r)).get("billNumber").asText();
            numbers.add(Long.parseLong(bn.substring(bn.lastIndexOf('-') + 1)));
        }
        numbers.sort(Long::compare);
        assertThat(new HashSet<>(numbers)).hasSize(THREADS);
        assertThat(numbers.get(THREADS - 1) - numbers.get(0)).as("no gaps").isEqualTo(THREADS - 1);
    }

    private static String content(MvcResult r) {
        try {
            return r.getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
