package com.github.kasuminova;

import com.github.balloonupdate.JavaAgentMain;
import net.minecraftforge.fml.relauncher.IFMLCallHook;

import java.util.Map;

public class StartUpdate implements IFMLCallHook {
    @Override
    public void injectData(Map<String, Object> data) {

    }

    @Override
    public Void call() {
        JavaAgentMain.premain(null,null);
        return null;
    }
}
