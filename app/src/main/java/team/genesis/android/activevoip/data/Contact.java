package team.genesis.android.activevoip.data;

import team.genesis.data.UUID;

public class Contact {
    public String alias;
    public UUID uuid;
    public enum Status{
        CONFIRM_WAIT,READY
    }
}
