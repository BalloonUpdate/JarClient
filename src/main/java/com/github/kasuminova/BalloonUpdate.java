package com.github.kasuminova;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.Name("BalloonUpdatePlugin")
@IFMLLoadingPlugin.TransformerExclusions("github.kasuminova")
public class BalloonUpdate implements IFMLLoadingPlugin {
    static Logger logger = LogManager.getLogger("AutoUpdaterMod");
    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return "com.github.kasuminova.StartUpdate";
    }

    @Override
    public void injectData(Map<String, Object> data) {

    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
