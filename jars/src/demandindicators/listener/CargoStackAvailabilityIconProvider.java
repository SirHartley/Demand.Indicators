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
        if (stack.isPickedUp()) return null;

        SubmarketAPI submarket = getSubmarketFor(stack);
        MarketAPI m = submarket != null ? submarket.getMarket() : null;

        if (submarket != null && m != null){
            if (stack.isCommodityStack() && !submarket.getPlugin().isFreeTransfer()) return getCommodityStackIconName(stack, m);
        }

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

    public String getCommodityStackIconName(CargoStackAPI stack, MarketAPI m){

        boolean hasLunaLib = Global.getSettings().getModManager().isModEnabled("lunalib");
        boolean showIndicator = !hasLunaLib || LunaSettings.getBoolean("demandIndicators", "demandIndicators_show");
        boolean lowVisMode = hasLunaLib && LunaSettings.getBoolean("demandIndicators", "demandIndicators_lowVis");
        boolean invert = hasLunaLib && LunaSettings.getBoolean("demandIndicators", "demandIndicators_invert");

        String imageNameExcessLow = Global.getSettings().getSpriteName("ui", "demandIndicators_commodityExcess_low" + (invert ? "_invert" : ""));
        String imageNameExcess = Global.getSettings().getSpriteName("ui", "demandIndicators_commodityExcess" + (invert ? "_invert" : ""));
        String imageNameExcessHigh = Global.getSettings().getSpriteName("ui", "demandIndicators_commodityExcess_high" + (invert ? "_invert" : ""));
        String imageNameShortageLow = Global.getSettings().getSpriteName("ui", "demandIndicators_commodityDeficit_low" + (invert ? "_invert" : ""));
        String imageNameShortage = Global.getSettings().getSpriteName("ui", "demandIndicators_commodityDeficit" + (invert ? "_invert" : ""));
        String imageNameShortageHigh = Global.getSettings().getSpriteName("ui", "demandIndicators_commodityDeficit_high" + (invert ? "_invert" : ""));

        if (showIndicator){
            CommodityOnMarketAPI data = m.getCommodityData(stack.getCommodityId());
            int econUnit = Math.round(data.getCommodity().getEconUnit());
            int excess = data.getExcessQuantity();
            int deficit = data.getDeficitQuantity();

            //low vis mode

            if (lowVisMode){
                if (excess > 0) return imageNameExcess;
                if (deficit > 0) return imageNameShortage;

                return null;
            }

            //high vis mode shows price guides

            if (excess > 0){
                if (excess > econUnit) return imageNameExcessHigh;
                else return imageNameExcess;
            }

            if (deficit > 0){
                if (deficit > econUnit) return imageNameShortageHigh;
                else return imageNameShortage;
            }

            float price = m.getSupplyPrice(stack.getCommodityId(), stack.getSize(), true) / stack.getSize();
            float defaultPrice = data.getCommodity().getBasePrice();

            if (price > defaultPrice) return imageNameShortageLow;
            else return imageNameExcessLow;
        }

        return null;
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