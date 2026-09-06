package ru.allin.menu;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.io.File;
import java.util.*;

public final class ALLINMenu extends JavaPlugin implements Listener, CommandExecutor {
    private YamlConfiguration menu, masters;
    private File menuFile, mastersFile;
    private final Map<UUID, TeleportSession> teleports = new HashMap<>();
    private final Map<UUID, String> openMenus = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        saveResource("menu.yml", false);
        saveResource("masters.yml", false);
        loadFiles();
        Objects.requireNonNull(getCommand("menu")).setExecutor(this);
        Objects.requireNonNull(getCommand("allinmenu")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ALLINMenu 1.0.2 enabled");
    }

    @Override public void onDisable() {
        teleports.values().forEach(TeleportSession::cancel);
        teleports.clear();
    }

    private void loadFiles() {
        reloadConfig();
        menuFile = new File(getDataFolder(), "menu.yml");
        mastersFile = new File(getDataFolder(), "masters.yml");
        menu = YamlConfiguration.loadConfiguration(menuFile);
        masters = YamlConfiguration.loadConfiguration(mastersFile);
    }

    private String c(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private List<String> c(List<String> list) { List<String> out=new ArrayList<>(); for(String s:list) out.add(c(s)); return out; }
    private void msg(Player p, String key) { p.sendMessage(c(getConfig().getString("messages.prefix","") + getConfig().getString("messages."+key,""))); }
    private void msg(Player p, String key, String name, int sec) {
        String s=getConfig().getString("messages.prefix","")+getConfig().getString("messages."+key,"");
        p.sendMessage(c(s.replace("{name}",name).replace("{seconds}",String.valueOf(sec))));
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!p.hasPermission("allinmenu.use")) { msg(p,"no-permission"); return true; }
            openMain(p); return true;
        }
        if (!sender.hasPermission("allinmenu.admin")) { if(sender instanceof Player p) msg(p,"no-permission"); return true; }
        if (args.length==0) { help(sender); return true; }
        if (args[0].equalsIgnoreCase("reload")) { loadFiles(); sender.sendMessage(c("&aALLINMenu перезагружен.")); return true; }
        if (args[0].equalsIgnoreCase("master")) return masterCommand(sender,args);
        help(sender); return true;
    }

    private boolean masterCommand(CommandSender s, String[] a) {
        if (a.length < 2) { masterHelp(s); return true; }
        String sub=a[1].toLowerCase(Locale.ROOT);
        try {
            if (sub.equals("list")) {
                ConfigurationSection sec=masters.getConfigurationSection("masters");
                s.sendMessage(c("&6Мастера обмена:"));
                if(sec==null || sec.getKeys(false).isEmpty()) s.sendMessage(c("&7Нет настроенных мастеров."));
                else for(String id:sec.getKeys(false)) s.sendMessage(c("&e"+id+"&7 — "+masters.getString("masters."+id+".name",id)));
                return true;
            }
            if (sub.equals("set") && s instanceof Player p && a.length>=4) {
                String id=a[2].toLowerCase(Locale.ROOT);
                String name=String.join(" ", Arrays.copyOfRange(a,3,a.length));
                Location l=p.getLocation();
                String path="masters."+id;
                masters.set(path+".name",name);
                masters.set(path+".world",l.getWorld().getName());
                masters.set(path+".x",l.getX()); masters.set(path+".y",l.getY()); masters.set(path+".z",l.getZ());
                masters.set(path+".yaw",l.getYaw()); masters.set(path+".pitch",l.getPitch());
                masters.save(mastersFile);
                s.sendMessage(c("&aМастер &e"+name+"&a сохранён в вашей текущей точке. ID: &f"+id));
                return true;
            }
            if (sub.equals("delete") && a.length>=3) {
                String id=a[2].toLowerCase(Locale.ROOT);
                masters.set("masters."+id,null); masters.save(mastersFile);
                s.sendMessage(c("&aМастер &e"+id+"&a удалён.")); return true;
            }
            if (sub.equals("rename") && a.length>=4) {
                String id=a[2].toLowerCase(Locale.ROOT);
                if(!masters.contains("masters."+id)){s.sendMessage(c("&cМастер не найден."));return true;}
                String name=String.join(" ",Arrays.copyOfRange(a,3,a.length));
                masters.set("masters."+id+".name",name); masters.save(mastersFile);
                s.sendMessage(c("&aНазвание изменено на &e"+name)); return true;
            }
        } catch(Exception e) { s.sendMessage(c("&cОшибка: "+e.getMessage())); getLogger().warning(e.toString()); return true; }
        masterHelp(s); return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(c("&6ALLINMenu admin:"));
        s.sendMessage(c("&e/allinmenu master set <id> <название> &7— создать/перенести мастера в текущую точку"));
        s.sendMessage(c("&e/allinmenu master rename <id> <название>"));
        s.sendMessage(c("&e/allinmenu master delete <id>"));
        s.sendMessage(c("&e/allinmenu master list"));
        s.sendMessage(c("&e/allinmenu reload"));
    }
    private void masterHelp(CommandSender s){ help(s); }

    private ItemStack item(Material mat,String name,List<String> lore) {
        ItemStack it=new ItemStack(mat); ItemMeta m=it.getItemMeta(); m.setDisplayName(c(name)); m.setLore(c(lore)); it.setItemMeta(m); return it;
    }
    private Material mat(String s, Material def){ try{return Material.valueOf(s.toUpperCase(Locale.ROOT));}catch(Exception e){return def;} }

    private void openMain(Player p) {
        int size=menu.getInt("size",54); String title=c(menu.getString("title","&0ALLINONLINE"));
        Inventory inv=Bukkit.createInventory(null,size,title);
        Material fill=mat(menu.getString("filler","BLACK_STAINED_GLASS_PANE"),Material.BLACK_STAINED_GLASS_PANE);
        ItemStack filler=item(fill," ",List.of());
        for(int i=0;i<size;i++) inv.setItem(i,filler);
        ConfigurationSection sec=menu.getConfigurationSection("items");
        if(sec!=null) for(String id:sec.getKeys(false)){
            String path="items."+id; int slot=menu.getInt(path+".slot",-1);
            if(slot>=0&&slot<size) inv.setItem(slot,item(mat(menu.getString(path+".material"),Material.PAPER),
                    menu.getString(path+".name",id),menu.getStringList(path+".lore")));
        }
        openMenus.put(p.getUniqueId(),"main"); p.openInventory(inv);
    }

    private void openJobs(Player p) {
        Inventory inv=Bukkit.createInventory(null,54,c(menu.getString("jobs.title","&0Работы")));
        ItemStack fill=item(Material.BLACK_STAINED_GLASS_PANE," ",List.of());
        for(int i=0;i<54;i++) inv.setItem(i,fill);
        ConfigurationSection sec=menu.getConfigurationSection("jobs.items");
        if(sec!=null) for(String id:sec.getKeys(false)){
            String path="jobs.items."+id; int slot=menu.getInt(path+".slot",-1);
            if(slot>=0&&slot<54) inv.setItem(slot,item(mat(menu.getString(path+".material"),Material.PAPER),
                    menu.getString(path+".name",id),menu.getStringList(path+".lore")));
        }
        openMenus.put(p.getUniqueId(),"jobs"); p.openInventory(inv);
    }

    private void openJobPage(Player p,String page) {
        String base="job-pages."+page;
        Inventory inv=Bukkit.createInventory(null,54,c(menu.getString(base+".title","&0Профессия")));
        ItemStack fill=item(Material.BLACK_STAINED_GLASS_PANE," ",List.of());
        for(int i=0;i<54;i++) inv.setItem(i,fill);
        inv.setItem(22,item(Material.WRITABLE_BOOK,"&e&lОписание и команды",menu.getStringList(base+".lines")));
        inv.setItem(49,item(Material.ARROW,"&eНазад",List.of("&7Вернуться к профессиям")));
        openMenus.put(p.getUniqueId(),"job:"+page); p.openInventory(inv);
    }

    @SuppressWarnings("unchecked")
    private void openCommandPage(Player p,int index) {
        List<Map<?,?>> pages=menu.getMapList("command-pages");
        if(pages.isEmpty()) return;
        index=Math.max(0,Math.min(index,pages.size()-1));
        Map<?,?> data=pages.get(index);
        Object titleObj=data.get("title"); String title=titleObj==null?"&0Команды":String.valueOf(titleObj);
        List<String> lines=new ArrayList<>();
        Object raw=data.get("lines");
        if(raw instanceof List<?> list) for(Object o:list) lines.add(String.valueOf(o));
        Inventory inv=Bukkit.createInventory(null,54,c(title));
        ItemStack fill=item(Material.BLACK_STAINED_GLASS_PANE," ",List.of());
        for(int i=0;i<54;i++) inv.setItem(i,fill);
        inv.setItem(22,item(Material.BOOK,"&b&lКоманды игрока",lines));
        if(index>0) inv.setItem(45,item(Material.ARROW,"&eПредыдущая страница",List.of()));
        inv.setItem(49,item(Material.BARRIER,"&cВ главное меню",List.of()));
        if(index+1<pages.size()) inv.setItem(53,item(Material.ARROW,"&eСледующая страница",List.of()));
        openMenus.put(p.getUniqueId(),"commands:"+index); p.openInventory(inv);
    }

    private void sendTelegram(Player p) {
        p.closeInventory();
        String url=menu.getString("telegram-url","https://t.me/allinonlinerp");
        p.sendMessage(c("&8&m--------------------------------"));
        p.sendMessage(c("&b&lALLINONLINE &f— Telegram"));
        Component button=Component.text("► ОТКРЫТЬ TELEGRAM ◄", NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl(url));
        p.sendMessage(button);
        p.sendMessage(c("&7"+url));
        p.sendMessage(c("&8&m--------------------------------"));
    }

    private void sendDonate(Player p) {
        p.closeInventory();
        String url=menu.getString("donate-url","https://allinonline.easydonate.ru");
        p.sendMessage(c("&8&m--------------------------------"));
        p.sendMessage(c("&d&lALLINONLINE &f— официальный донат-магазин"));
        Component button=Component.text("► ОТКРЫТЬ МАГАЗИН ◄", NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl(url));
        p.sendMessage(button);
        p.sendMessage(c("&7"+url));
        p.sendMessage(c("&8&m--------------------------------"));
    }

    private void openInfo(Player p,String page) {
        String base="pages."+page;
        String title=c(menu.getString(base+".title","&0Информация"));
        Inventory inv=Bukkit.createInventory(null,54,title);
        ItemStack fill=item(Material.BLACK_STAINED_GLASS_PANE," ",List.of());
        for(int i=0;i<54;i++) inv.setItem(i,fill);
        List<String> lines=menu.getStringList(base+".lines");
        inv.setItem(22,item(Material.BOOK,"&e&lИнформация",lines));
        inv.setItem(49,item(Material.ARROW,"&eНазад",List.of("&7Вернуться в главное меню")));
        openMenus.put(p.getUniqueId(),"page:"+page); p.openInventory(inv);
    }

    private void openMasters(Player p) {
        ConfigurationSection sec=masters.getConfigurationSection("masters");
        if(sec==null||sec.getKeys(false).isEmpty()){msg(p,"no-masters");return;}
        List<String> ids=new ArrayList<>(sec.getKeys(false));
        int size=Math.min(54, Math.max(9, ((ids.size()+8)/9)*9));
        Inventory inv=Bukkit.createInventory(null,size,c("&0Мастера обмена"));
        for(int i=0;i<ids.size()&&i<size;i++){
            String id=ids.get(i), name=masters.getString("masters."+id+".name",id);
            ItemStack it=item(Material.EMERALD,"&a&l"+name,List.of("&7Телепортация: &f"+getConfig().getInt("teleport.seconds",30)+" сек.","&cВо время ожидания нельзя двигаться.","","&eНажмите для телепортации."));
            ItemMeta im=it.getItemMeta(); im.getPersistentDataContainer().set(new NamespacedKey(this,"master-id"),org.bukkit.persistence.PersistentDataType.STRING,id); it.setItemMeta(im);
            inv.setItem(i,it);
        }
        openMenus.put(p.getUniqueId(),"masters"); p.openInventory(inv);
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if(!(e.getWhoClicked() instanceof Player p))return;
        String type=openMenus.get(p.getUniqueId()); if(type==null)return;
        e.setCancelled(true);
        if(e.getClickedInventory()==null||e.getCurrentItem()==null)return;
        int slot=e.getRawSlot();
        if(type.equals("main")){
            ConfigurationSection sec=menu.getConfigurationSection("items"); if(sec==null)return;
            for(String id:sec.getKeys(false)){
                String path="items."+id; if(menu.getInt(path+".slot",-1)!=slot)continue;
                String action=menu.getString(path+".action","");
                String page=menu.getString(path+".page","");
                if(action.equalsIgnoreCase("close")) p.closeInventory();
                else if(action.equalsIgnoreCase("masters")) openMasters(p);
                else if(action.equalsIgnoreCase("jobs")) openJobs(p);
                else if(action.equalsIgnoreCase("commands")) openCommandPage(p,0);
                else if(action.equalsIgnoreCase("donate")) sendDonate(p);
                else if(action.equalsIgnoreCase("telegram")) sendTelegram(p);
                else if(!page.isBlank()) openInfo(p,page);
                return;
            }
         } else if(type.startsWith("page:") && slot==49) openMain(p);
        else if(type.equals("jobs")) {
            if(slot==49){openMain(p);return;}
            ConfigurationSection sec=menu.getConfigurationSection("jobs.items"); if(sec==null)return;
            for(String id:sec.getKeys(false)){
                String path="jobs.items."+id;
                if(menu.getInt(path+".slot",-1)!=slot) continue;
                String action=menu.getString(path+".action","");
                String page=menu.getString(path+".page","");
                if(action.equalsIgnoreCase("back")) openMain(p);
                else if(!page.isBlank()) openJobPage(p,page);
                return;
            }
        } else if(type.startsWith("job:") && slot==49) openJobs(p);
        else if(type.startsWith("commands:")){
            int idx=Integer.parseInt(type.substring("commands:".length()));
            if(slot==45 && idx>0) openCommandPage(p,idx-1);
            else if(slot==49) openMain(p);
            else if(slot==53 && idx+1<menu.getMapList("command-pages").size()) openCommandPage(p,idx+1);
        }
        else if(type.equals("masters")){
            ItemMeta im=e.getCurrentItem().getItemMeta(); if(im==null)return;
            String id=im.getPersistentDataContainer().get(new NamespacedKey(this,"master-id"),org.bukkit.persistence.PersistentDataType.STRING);
            if(id!=null){p.closeInventory();startTeleport(p,id);}
        }
    }

    @EventHandler public void close(org.bukkit.event.inventory.InventoryCloseEvent e){ openMenus.remove(e.getPlayer().getUniqueId()); }

    private Location masterLocation(String id) {
        String path="masters."+id; String wn=masters.getString(path+".world");
        World w=wn==null?null:Bukkit.getWorld(wn); if(w==null)return null;
        return new Location(w,masters.getDouble(path+".x"),masters.getDouble(path+".y"),masters.getDouble(path+".z"),
                (float)masters.getDouble(path+".yaw"),(float)masters.getDouble(path+".pitch"));
    }

    private void startTeleport(Player p,String id) {
        Location target=masterLocation(id); if(target==null){msg(p,"master-not-found");return;}
        if(teleports.containsKey(p.getUniqueId())) teleports.remove(p.getUniqueId()).cancel();
        int total=Math.max(1,getConfig().getInt("teleport.seconds",30));
        String name=masters.getString("masters."+id+".name",id);
        Location anchor=p.getLocation().clone();
        TeleportSession session=new TeleportSession(p,id,name,anchor,target,total);
        teleports.put(p.getUniqueId(),session); session.start();
        msg(p,"teleport-start",name,total);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void move(PlayerMoveEvent e) {
        TeleportSession s=teleports.get(e.getPlayer().getUniqueId()); if(s==null||e.getTo()==null)return;
        Location a=s.anchor, to=e.getTo();
        if(a.getWorld()!=to.getWorld() || Math.abs(a.getX()-to.getX())>0.001 || Math.abs(a.getY()-to.getY())>0.001 || Math.abs(a.getZ()-to.getZ())>0.001){
            e.setTo(new Location(a.getWorld(),a.getX(),a.getY(),a.getZ(),to.getYaw(),to.getPitch()));
            s.cancel(); teleports.remove(e.getPlayer().getUniqueId()); msg(e.getPlayer(),"teleport-cancel-move");
        }
    }

    @EventHandler(ignoreCancelled=true) public void damage(EntityDamageEvent e){
        if(!getConfig().getBoolean("teleport.cancel-on-damage",false)||!(e.getEntity() instanceof Player p))return;
        TeleportSession s=teleports.remove(p.getUniqueId()); if(s!=null){s.cancel();msg(p,"teleport-cancel-damage");}
    }

    private final class TeleportSession {
        final Player p; final String id,name; final Location anchor,target; int left; BukkitTask task;
        TeleportSession(Player p,String id,String name,Location anchor,Location target,int left){this.p=p;this.id=id;this.name=name;this.anchor=anchor;this.target=target;this.left=left;}
        void start(){
            task=Bukkit.getScheduler().runTaskTimer(ALLINMenu.this,()->{
                if(!p.isOnline()){cancel();teleports.remove(p.getUniqueId());return;}
                if(left<=0){
                    cancel(); teleports.remove(p.getUniqueId());
                    p.teleportAsync(target).thenRun(()->Bukkit.getScheduler().runTask(ALLINMenu.this,()->msg(p,"teleport-done",name,0)));
                    return;
                }
                p.sendActionBar(c(getConfig().getString("messages.teleport-progress","&eТелепортация: &c{seconds} сек.").replace("{seconds}",String.valueOf(left))));
                if(left<=5 || left%5==0) p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_HAT,0.5f,1.2f);
                left--;
            },0L,20L);
        }
        void cancel(){if(task!=null&&!task.isCancelled())task.cancel();}
    }
}
