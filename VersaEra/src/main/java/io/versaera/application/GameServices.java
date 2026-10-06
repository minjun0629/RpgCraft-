package io.versaera.application;

import io.versaera.application.port.*;
import io.versaera.content.ContentBundle;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.hidden.HiddenRule;
import io.versaera.domain.hidden.PlayerFacts;
import io.versaera.domain.world.RegionIndex;
import io.versaera.persistence.*;

import java.time.ZoneId;
import java.util.Collection;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * 조립 지점 (플러그인과 테스트가 같은 방식으로 서비스를 만든다).
 * 모든 서비스 호출은 DbExecutor 의 한 스레드에서 해야 한다.
 */
public final class GameServices {
    public final GameClock clock;
    public final EventBus bus;
    public final ContentBundle content;
    public final TxRunner tx;
    public final ItemRepository itemRepo;
    public final ProgressRepository progress;
    public final AuditLog audit;
    public final ItemService items;
    public final EconomyService economy;
    public final TradeService trades;
    public final GrowthService growth;
    public final CraftingService crafting;
    public final ExplorationService exploration;
    public final RelationService relations;
    public final ProfileService profiles;
    public final RegionIndex regions;
    private HiddenService hidden;

    public GameServices(Database db, ContentBundle content, GameClock clock, ZoneId zone, Logger log) {
        this.clock = clock;
        this.bus = new EventBus(log);
        this.content = content;
        this.tx = new SqlTxRunner(db);
        this.itemRepo = new JdbcItemRepository(db);
        this.progress = new JdbcProgressRepository(db);
        this.audit = new JdbcAuditLog(db, clock);
        WalletRepository wallets = new JdbcWalletRepository(db);
        ItemTypeRegistry types = new ItemTypeRegistry(content.items());
        this.items = new ItemService(tx, itemRepo, new JdbcDeliveryRepository(db), types, audit, bus, clock);
        this.economy = new EconomyService(tx, wallets, audit, bus, clock);
        this.trades = new TradeService(tx, new JdbcTradeRepository(db), itemRepo, types, wallets, economy, audit, bus, clock);
        this.growth = new GrowthService(tx, progress, content.disciplines(), content.stats(), bus);
        this.crafting = new CraftingService(tx, content.recipes(), items, growth, progress, audit, bus);
        this.regions = new RegionIndex(content.regions());
        this.exploration = new ExplorationService(tx, progress, regions, growth, bus, clock);
        this.relations = new RelationService(tx, progress, content.npcs(), growth, bus, clock, zone);
        this.profiles = new ProfileService(tx, new JdbcProfileRepository(db), clock);
        for (var r : content.resources()) {
            growth.discipline(r.discipline());
            types.get(r.yield());
        }
    }

    /** 봉인을 연 히든 규칙을 붙인다 (없으면 히든 콘텐츠 없이 동작) */
    public HiddenService attachHidden(Collection<HiddenRule> rules, Function<String, PlayerFacts> facts) {
        hidden = new HiddenService(tx, progress, rules, facts, bus, clock);
        growth.onCounter(hidden::counterChanged);
        return hidden;
    }

    public HiddenService hidden() {
        return hidden;
    }
}
