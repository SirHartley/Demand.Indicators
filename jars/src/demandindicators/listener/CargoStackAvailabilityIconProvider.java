package demandindicators.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.*;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import lunalib.lunaSettings.LunaSettings;

import java.awt.*;

public class CargoStackAvailabilityIconProvider implements CommodityIconProvider {

    protected transient SubmarketAPI currSubmarket = null;

    public static void register(){
        GenericPluginManagerAPI plugins = Global.getSector().getGenericPlugins();

        if (plugins.getPluginsOfClass(CargoStackAvailabilityIconProvider.class).isEmpty()) {
            CargoStackAvailabilityIconProvider p = new CargoStackAvailabilityIconProvider();
            plugins.addPlugin(p, true);
        }
    }

    public int getHandlingPriority(Object params) {
        if (params instanceof PlayerFleetPersonnelTracker.CommodityIconProviderWrapper wrapper
                && getArrowIconName(wrapper.stack) != null) {
            return GenericPluginManagerAPI.MOD_SUBSET;
        }

        return -1;
    }

    public String getRankIconName(CargoStackAPI stack) {
        return getArrowIconName(stack);
    }

    // the game asks one provider per stack, so the next one keeps its icon on stacks we claim
    public String getIconName(CargoStackAPI stack) {
        CommodityIconProvider next = getNextProvider(stack);
        return next != null ? next.getIconName(stack) : null;
    }

    public String getArrowIconName(CargoStackAPI stack) {
        if (stack.isPickedUp() || !stack.isCommodityStack() || !getSetting("demandIndicators_show", true)) return null;

        if (stack.isInPlayerCargo()) {
            String mode = Global.getSettings().getModManager().isModEnabled("lunalib")
                    ? LunaSettings.getString("demandIndicators", "demandIndicators_playerCargo")
                    : "Off";
            boolean sellHints = "Sell Hints".equals(mode);
            if (!sellHints && !"Always Show".equals(mode)) return null;

            MarketAPI m = getTradeMarket();
            if (m == null || sellHints && hasStock(m, stack.getCommodityId())) return null;

            return getCommodityStackIconName(stack, m, true, sellHints);
        }

        SubmarketAPI submarket = getSubmarketFor(stack);
        MarketAPI m = submarket != null ? submarket.getMarket() : null;

        if (m != null && !submarket.getPlugin().isFreeTransfer()) return getCommodityStackIconName(stack, m, false, false);

        return null;
    }

    public CommodityIconProvider getNextProvider(CargoStackAPI stack) {
        Object params = new PlayerFleetPersonnelTracker.CommodityIconProviderWrapper(stack);
        CommodityIconProvider best = null;
        int bestPriority = -1;

        for (GenericPluginManagerAPI.GenericPlugin plugin : Global.getSector().getGenericPlugins().getPluginsOfClass(CommodityIconProvider.class)) {
            if (plugin == this) continue;

            int priority = plugin.getHandlingPriority(params);
            if (priority > bestPriority) {
                best = (CommodityIconProvider) plugin;
                bestPriority = priority;
            }
        }

        return best;
    }

    public String getCommodityStackIconName(CargoStackAPI stack, MarketAPI m, boolean playerCargo, boolean sellHints){
        boolean lowVisMode = getSetting("demandIndicators_lowVis", false);
        String suffix = getSetting("demandIndicators_invert", false) ? "_invert" : "";

        CommodityOnMarketAPI data = m.getCommodityData(stack.getCommodityId());
        int econUnit = Math.round(data.getCommodity().getEconUnit());
        int excess = data.getExcessQuantity();
        int deficit = data.getDeficitQuantity();
        float defaultPrice = data.getCommodity().getBasePrice();

        if (sellHints && getUnitPrice(stack, m, true) <= defaultPrice) return null;

        if (lowVisMode){
            if (excess > 0) return sellHints ? null : getSprite("demandIndicators_commodityExcess", suffix);
            if (deficit > 0) return getSprite("demandIndicators_commodityDeficit", suffix);

            return null;
        }

        if (excess > 0) return sellHints ? null : getSprite(excess > econUnit ? "demandIndicators_commodityExcess_high" : "demandIndicators_commodityExcess", suffix);
        if (deficit > 0) return getSprite(deficit > econUnit ? "demandIndicators_commodityDeficit_high" : "demandIndicators_commodityDeficit", suffix);

        if (getUnitPrice(stack, m, playerCargo) > defaultPrice) return getSprite("demandIndicators_commodityDeficit_low", suffix);
        return getSprite("demandIndicators_commodityExcess_low", suffix);
    }

    // player cargo compares the sell price, market cargo the buy price
    private static float getUnitPrice(CargoStackAPI stack, MarketAPI m, boolean playerCargo) {
        float total = playerCargo
                ? m.getDemandPrice(stack.getCommodityId(), stack.getSize(), true)
                : m.getSupplyPrice(stack.getCommodityId(), stack.getSize(), true);
        return total / stack.getSize();
    }

    private static String getSprite(String key, String suffix) {
        return Global.getSettings().getSpriteName("ui", key + suffix);
    }

    private static boolean getSetting(String id, boolean fallback) {
        if (!Global.getSettings().getModManager().isModEnabled("lunalib")) return fallback;
        return LunaSettings.getBoolean("demandIndicators", id);
    }

    public MarketAPI getTradeMarket() {
        SectorEntityToken entity = getInteractionEntity();
        MarketAPI m = entity != null ? entity.getMarket() : null;
        if (m == null) return null;

        for (SubmarketAPI sub : m.getSubmarketsCopy()) {
            if (!sub.getPlugin().isFreeTransfer() && !sub.getPlugin().isHidden()) return m;
        }

        return null;
    }

    public boolean hasStock(MarketAPI m, String commodityId) {
        for (SubmarketAPI sub : m.getSubmarketsCopy()) {
            if (!sub.getPlugin().isFreeTransfer() && !sub.getPlugin().isHidden() && sub.getCargo().getCommodityQuantity(commodityId) > 0) return true;
        }

        return false;
    }

    public SectorEntityToken getInteractionEntity() {
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        SectorEntityToken entity = null;
        if (dialog != null) {
            entity = dialog.getInteractionTarget();
            if (entity != null && entity.getMarket() != null && entity.getMarket().getPrimaryEntity() != null) {
                entity = entity.getMarket().getPrimaryEntity();
            }
        }
        return entity;
    }

    /**
     * Assumes stack is not in player cargo.
     *
     * @param stack
     * @return
     */
    public SubmarketAPI getSubmarketFor(CargoStackAPI stack) {
        if (stack.getCargo() == null) return null;
        SectorEntityToken entity = getInteractionEntity();
        if (entity == null || entity.getMarket() == null || entity.getMarket().getSubmarketsCopy() == null)
            return currSubmarket;

        for (SubmarketAPI sub : entity.getMarket().getSubmarketsCopy()) {
            if (sub.getCargo() == stack.getCargo()) {
                return sub;
            }
        }
        return currSubmarket;
    }
}