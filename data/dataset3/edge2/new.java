import java.util.ArrayList;
import java.util.List;
public class ShoppingCart {
    private List<Item> items;
    private double totalPrice;   
    public ShoppingCart() {
        this.items = new ArrayList<>();
        this.totalPrice = 0.0;
    }   
    public void addItem(Item item) {
        items.add(item);
        totalPrice += item.getPrice();
    }
    public double getTotalPrice() {
        return totalPrice;
    }}
    