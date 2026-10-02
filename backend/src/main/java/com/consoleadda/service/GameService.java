package com.consoleadda.service;

import com.consoleadda.dto.game.GameRequest;
import com.consoleadda.dto.game.GameResponse;
import com.consoleadda.entity.GameMaster;
import com.consoleadda.entity.GameRental;
import com.consoleadda.entity.RentalStatus;
import com.consoleadda.exception.BusinessException;
import com.consoleadda.exception.NotFoundException;
import com.consoleadda.mapper.RentalMapper;
import com.consoleadda.repository.GameMasterRepository;
import com.consoleadda.repository.GameRentalRepository;
import com.consoleadda.service.billing.Money;
import com.consoleadda.service.billing.RentalCalculator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Game master (physical discs) with live availability and return on investment per disc. */
@Service
public class GameService {

    private final GameMasterRepository repository;
    private final GameRentalRepository rentalRepository;
    private final RentalMapper rentalMapper;
    private final AuditService auditService;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public GameService(GameMasterRepository repository, GameRentalRepository rentalRepository,
                       RentalMapper rentalMapper, AuditService auditService, JdbcTemplate jdbc, Clock clock) {
        this.repository = repository;
        this.rentalRepository = rentalRepository;
        this.rentalMapper = rentalMapper;
        this.auditService = auditService;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GameResponse> list(boolean includeInactive, boolean admin) {
        Map<Long, GameRental> out = rentalRepository.findByStatus(RentalStatus.OUT).stream()
                .collect(Collectors.toMap(r -> r.getGame().getId(), Function.identity()));
        Map<Long, RentalCalculator.Roi> roi = admin ? roiByGame() : Map.of();
        Instant now = clock.instant();
        Duration grace = rentalMapper.grace();
        return repository.findAllByOrderByNameAscCopyNumberAsc().stream()
                .filter(g -> includeInactive || g.isActive())
                .map(g -> toResponse(g, out.get(g.getId()), roi.get(g.getId()), admin, now, grace))
                .toList();
    }

    @Transactional(readOnly = true)
    public GameResponse get(Long id, boolean admin) {
        GameMaster game = load(id);
        GameRental current = rentalRepository.findByStatus(RentalStatus.OUT).stream()
                .filter(r -> r.getGame().getId().equals(id)).findFirst().orElse(null);
        return toResponse(game, current, admin ? roiByGame().get(id) : null, admin, clock.instant(),
                rentalMapper.grace());
    }

    @Transactional
    public GameResponse create(GameRequest request) {
        if (repository.existsByNameIgnoreCaseAndCopyNumber(request.name().trim(), request.copyNumber())) {
            throw duplicate(request);
        }
        GameMaster game = new GameMaster();
        apply(game, request);
        repository.saveAndFlush(game);
        auditService.record("GAME_CREATED", "GameMaster", game.getId(), null, snapshot(game));
        return get(game.getId(), true);
    }

    @Transactional
    public GameResponse update(Long id, GameRequest request) {
        GameMaster game = load(id);
        if (repository.existsByNameIgnoreCaseAndCopyNumberAndIdNot(request.name().trim(), request.copyNumber(), id)) {
            throw duplicate(request);
        }
        Map<String, Object> before = snapshot(game);
        apply(game, request);
        repository.saveAndFlush(game);
        auditService.record("GAME_UPDATED", "GameMaster", id, before, snapshot(game));
        return get(id, true);
    }

    /**
     * Revenue per disc = rental charges + late fees of rentals that were not cancelled (amounts before GST,
     * deposits excluded).
     */
    private Map<Long, RentalCalculator.Roi> roiByGame() {
        Map<Long, BigDecimal> revenue = new HashMap<>();
        Map<Long, Long> count = new HashMap<>();
        jdbc.query("""
                SELECT game_id, COUNT(*) AS rentals, SUM(rental_price + late_fee) AS revenue
                FROM game_rentals WHERE status <> 'CANCELLED' GROUP BY game_id""", rs -> {
            revenue.put(rs.getLong("game_id"), rs.getBigDecimal("revenue"));
            count.put(rs.getLong("game_id"), rs.getLong("rentals"));
        });
        Map<Long, RentalCalculator.Roi> result = new HashMap<>();
        for (GameMaster g : repository.findAll()) {
            result.put(g.getId(), RentalCalculator.roi(g.getPurchasePrice(), g.getRentalPrice(),
                    revenue.getOrDefault(g.getId(), Money.ZERO), count.getOrDefault(g.getId(), 0L)));
        }
        return result;
    }

    GameMaster load(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Game", id));
    }

    private static void apply(GameMaster g, GameRequest r) {
        g.setName(r.name().trim());
        g.setCopyNumber(r.copyNumber());
        g.setPlatform(StringUtils.hasText(r.platform()) ? r.platform().trim() : "PS5");
        g.setPurchasePrice(Money.round(r.purchasePrice()));
        g.setPurchaseDate(r.purchaseDate());
        g.setRentalPrice(Money.round(r.rentalPrice()));
        g.setRentalPeriodDays(r.rentalPeriodDays());
        g.setLateFeePerDay(Money.round(r.lateFeePerDay()));
        g.setDefaultDeposit(Money.round(r.defaultDeposit()));
        g.setAvailableForRental(r.availableForRental());
        g.setActive(r.active());
        g.setNotes(StringUtils.hasText(r.notes()) ? r.notes().trim() : null);
    }

    private static Map<String, Object> snapshot(GameMaster g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", g.getName());
        m.put("copyNumber", g.getCopyNumber());
        m.put("purchasePrice", g.getPurchasePrice());
        m.put("rentalPrice", g.getRentalPrice());
        m.put("rentalPeriodDays", g.getRentalPeriodDays());
        m.put("lateFeePerDay", g.getLateFeePerDay());
        m.put("defaultDeposit", g.getDefaultDeposit());
        m.put("availableForRental", g.isAvailableForRental());
        m.put("active", g.isActive());
        return m;
    }

    private static BusinessException duplicate(GameRequest r) {
        return BusinessException.conflict("DUPLICATE_GAME", "'" + r.name().trim() + "' copy " + r.copyNumber()
                + " already exists. Use another copy number for a second disc.");
    }

    private GameResponse toResponse(GameMaster g, GameRental current, RentalCalculator.Roi roi, boolean admin,
                                    Instant now, Duration grace) {
        GameResponse.CurrentRental rental = current == null ? null : new GameResponse.CurrentRental(current.getId(),
                current.getRentalNumber(), current.getCustomerName(), current.getExpectedReturnAt(),
                rentalMapper.isOverdue(current, now, grace));
        GameResponse.Roi roiDto = roi == null ? null : new GameResponse.Roi(roi.purchaseCost(), roi.revenue(),
                roi.rentals(), roi.remaining(), roi.roiPercent(), roi.breakEvenRentals(), roi.rentalsToRecover(),
                roi.recovered());
        return new GameResponse(g.getId(), g.getName(), g.getCopyNumber(), g.getPlatform(),
                admin ? g.getPurchasePrice() : null, admin ? g.getPurchaseDate() : null, g.getRentalPrice(),
                g.getRentalPeriodDays(), g.getLateFeePerDay(), g.getDefaultDeposit(), g.isAvailableForRental(),
                g.isActive(), g.getNotes(), rental, roiDto);
    }
}
