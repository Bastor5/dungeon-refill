package dev.dungeonrefill;

/** One item the mod keeps topped up. {@code id} is the SkyBlock item ID, which is also what /gfs expects. */
public class RefillItem {
    public String id;
    public String name;
    public int amount;
    public boolean enabled;
    /** Added by the user in the menu (can be removed), as opposed to a built-in default. */
    public boolean custom;

    public RefillItem() {
    }

    public RefillItem(String id, String name, int amount, boolean enabled, boolean custom) {
        this.id = id;
        this.name = name;
        this.amount = amount;
        this.enabled = enabled;
        this.custom = custom;
    }
}
