package demandindicators.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import demandindicators.listener.CargoStackAvailabilityIconProvider;

public class ModPlugin extends BaseModPlugin {

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        CargoStackAvailabilityIconProvider.register();
    }
}
