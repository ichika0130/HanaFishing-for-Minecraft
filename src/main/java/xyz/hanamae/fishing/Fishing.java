package xyz.hanamae.fishing;

import org.bukkit.plugin.java.JavaPlugin;
import xyz.hanamae.listener.MyListener;

public final class Fishing extends JavaPlugin {

    @Override
    public void onEnable() {
        // 1. 注册监听器，并把当前插件实例(this)传给 MyListener
        // 这样 MyListener 才能创建 NamespacedKey
        getServer().getPluginManager().registerEvents(new MyListener(this), this);

        getLogger().info("HanaFishing Enable");
    }

    @Override
    public void onDisable() {
        // 卸载逻辑
    }
}