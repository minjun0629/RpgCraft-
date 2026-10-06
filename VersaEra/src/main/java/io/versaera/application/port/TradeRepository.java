package io.versaera.application.port;

import java.util.List;

public interface TradeRepository {
    void open(String id, String a, String b, long at);

    void putOffer(String tradeId, String itemId, String owner);

    void removeOffer(String itemId);

    /** item → 원래 주인 */
    List<String[]> offers(String tradeId);

    void close(String id, String state, long aMoney, long bMoney, long at);

    String state(String id);

    List<String> openTradeIds();
}
