public class DataProcessor {
    public void processData(String[] data) {
        for (String item : data) {
            String cleaned = item.trim().toLowerCase();
            if (!cleaned.isEmpty()) {
                System.out.println(cleaned);
            }
        }
    }
}