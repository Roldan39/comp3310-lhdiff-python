public class StringUtils {
    public static boolean isEmpty(String str) {
        return str.length() == 0;
    }
    
    public static String trim(String str) {
        return str.trim();
    }
    
    public static String reverse(String str) {
        StringBuilder sb = new StringBuilder(str);
        return sb.reverse().toString();
    }
}