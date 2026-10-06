package io.versaera.persistence;

import io.versaera.application.port.TradeRepository;

import java.util.List;

public final class JdbcTradeRepository implements TradeRepository {
    private final Jdbc j;

    public JdbcTradeRepository(Database db) {
        this.j = new Jdbc(db);
    }

    @Override
    public void open(String id, String a, String b, long at) {
        j.update("INSERT INTO trade (id, a_uuid, b_uuid, state, created_at) VALUES (?, ?, ?, 'OPEN', ?)", ps -> {
            ps.setString(1, id);
            ps.setString(2, a);
            ps.setString(3, b);
            ps.setLong(4, at);
        });
    }

    @Override
    public void putOffer(String tradeId, String itemId, String owner) {
        j.update("INSERT INTO trade_offer (item_id, trade_id, owner_uuid) VALUES (?, ?, ?)", ps -> {
            ps.setString(1, itemId);
            ps.setString(2, tradeId);
            ps.setString(3, owner);
        });
    }

    @Override
    public void removeOffer(String itemId) {
        j.update("DELETE FROM trade_offer WHERE item_id = ?", ps -> ps.setString(1, itemId));
    }

    @Override
    public List<String[]> offers(String tradeId) {
        return j.query("SELECT item_id, owner_uuid FROM trade_offer WHERE trade_id = ?", ps -> ps.setString(1, tradeId),
                rs -> new String[]{rs.getString(1), rs.getString(2)});
    }

    @Override
    public void close(String id, String state, long aMoney, long bMoney, long at) {
        int n = j.update("UPDATE trade SET state = ?, a_money = ?, b_money = ?, closed_at = ? WHERE id = ? AND state = 'OPEN'", ps -> {
            ps.setString(1, state);
            ps.setLong(2, aMoney);
            ps.setLong(3, bMoney);
            ps.setLong(4, at);
            ps.setString(5, id);
        });
        if (n != 1) throw new IllegalStateException("이미 닫힌 거래입니다: " + id);
        j.update("DELETE FROM trade_offer WHERE trade_id = ?", ps -> ps.setString(1, id));
    }

    @Override
    public String state(String id) {
        return j.one("SELECT state FROM trade WHERE id = ?", ps -> ps.setString(1, id), rs -> rs.getString(1), null);
    }

    @Override
    public List<String> openTradeIds() {
        return j.query("SELECT id FROM trade WHERE state = 'OPEN'", ps -> { }, rs -> rs.getString(1));
    }
}
