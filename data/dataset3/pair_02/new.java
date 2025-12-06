public class StringUtils {
    public static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }
    
    public static String trim(String str) {
        if (str == null) {
            return null;
        }
        return str.trim();
    }
    
    public static String reverse(String str) {
        if (str == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(str);
        return sb.reverse().toString();
    }
}