package demandindicators.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.CommodityMarketDataAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.*;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import lunalib.lunaSettings.LunaSettings;

import java.awt.*;

public class CargoStackAvailabilityIconProvider implements CommodityIconProvider {

    protected transient SubmarketAPI currSubmarket = null;

    public static void register() {
        GenericPluginManagerAPI plugins = Global.getSector().getGenericPlugins();
        if (plugins.getPluginsOfClass(CargoStackAvailabilityIconProvider.class).isEmpty()) {
            CargoStackAvailabilityIconProvider p = new CargoStackAvailabilityIconProvider();
            plugins.addPlugin(p, true);
        }
    }

    public int getHandlingPriority(Object params) {
        return GenericPluginManagerAPI.MOD_GENERAL;
    }

    public String getRankIconName(CargoStackAPI stack) {
        if (stack.isPickedUp() || stack.isInPlayerCargo() || !stack.isCommodityStack()) return getDefaultMarineIcon(stack);
        SubmarketAPI submarket = getSubmarketFor(stack);

        if (submarket == null) return getDefaultMarineIcon(stack);
        if (submarket.getPlugin().isFreeTransfer()) return getDefaultMarineIcon(stack);

        MarketAPI m = submarket.getMarket();

        if (m == null) return getDefaultMarineIcon(stack);

        CommodityOnMarketAPI data = m.getCommodityData(stack.getCommodityId());
        int econUnit = Math.round(data.getCommodity().getEconUnit());

        int excess = data.getExcessQuantity();
        int deficit = data.getDeficitQuantity();

        //low vis mode only shows indicators on shortage

        boolean lowVisMode = LunaSettings.getBoolean("demandIndicators", "demandIndicators_lowVis");

        if (lowVisMode){
            if (excess > 0) return Global.getSettings().getSpriteName("ui", "demandIndicators_commodityExcess");
            if (deficit > 0) return Global.getSettings().getSpriteName("ui", "demandIndicators_commodityDeficit");

            return getDefaultMarineIcon(stack);
        }

        //high vis mode shows price guides

        if (excess > 0){
            if (excess > econUnit) return Global.getSettings().getSpriteName("ui", "demandIndicators_commodityExcess_high");
            else return Global.getSettings().getSpriteName("ui", "demandIndicators_commodityExcess");
        }

        if (deficit > 0){
            if (deficit > econUnit) return Global.getSettings().getSpriteName("ui", "demandIndicators_commodityDeficit_high");
            else return Global.getSettings().getSpriteName("ui", "demandIndicators_commodityDeficit");
        }

        float price = m.getSupplyPrice(stack.getCommodityId(), stack.getSize(), true) / stack.getSize();
        float defaultPrice = data.getCommodity().getBasePrice();

        if (price > defaultPrice) return Global.getSettings().getSpriteName("ui", "demandIndicators_commodityDeficit_low");
        else return Global.getSettings().getSpriteName("ui", "demandIndicators_commodityExcess_low");
    }

    public String getDefaultMarineIcon(CargoStackAPI stack){
        return PlayerFleetPersonnelTracker.getInstance().getRankIconName(stack);
    }

    public String getIconName(CargoStackAPI stack) {
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