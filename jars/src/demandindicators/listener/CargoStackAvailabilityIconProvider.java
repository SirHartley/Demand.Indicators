package demandindicators.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.*;

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
        if (stack.isPickedUp() || stack.isInPlayerCargo() || !stack.isCommodityStack()) return null;
        SubmarketAPI submarket = getSubmarketFor(stack);

        if (submarket == null) return null;
        if (submarket.getPlugin().isFreeTransfer()) return null;

        MarketAPI m = submarket.getMarket();

        if (m == null) return null;

        int excess = m .getCommodityData(stack.getCommodityId()).getExcessQuantity();
        int deficit = m.getCommodityData(stack.getCommodityId()).getDeficitQuantity();

        if (excess > 0) return Global.getSettings().getSpriteName("ui", "demandIndicators_commodityExcess");
        if (deficit > 0) return Global.getSettings().getSpriteName("ui", "demandIndicators_commodityDeficit");

        return null;
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