public class DataProcessor {
    public void processData(String[] data) {
        for (String item : data) {
            String cleaned = cleanItem(item);
            if (isValid(cleaned)) {
                printItem(cleaned);
            }
        }
    }
    
    private String cleanItem(String item) {
        return item.trim().toLowerCase();
    }
    
    private boolean isValid(String item) {
        return !item.isEmpty();
    }
    
    private void printItem(String item) {
        System.out.println(item);
    }
}