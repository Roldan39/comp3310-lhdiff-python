package org.example.handler;

public class OrderStatusHandler {
    
    public String getStatusMessage(String status) {
        switch (status) {
            case "PENDING":
                return "Order is pending processing";
            case "CONFIRMED":
                return "Order has been confirmed";
            case "SHIPPED":
                return "Order has been shipped";
            case "DELIVERED":
                return "Order has been delivered";
            case "CANCELLED":
                return "Order has been cancelled";
            case "REFUNDED":
                return "Order has been refunded";
            default:
                return "Unknown order status";
        }
    }
    
    public int getProcessingTime(String priority) {
        switch (priority) {
            case "HIGH":
                return 1;
            case "MEDIUM":
                return 3;
            case "LOW":
                return 7;
            default:
                return 5;
        }
    }
    
    public double getShippingCost(String method) {
        switch (method) {
            case "EXPRESS":
                return 29.99;
            case "STANDARD":
                return 9.99;
            case "ECONOMY":
                return 4.99;
            default:
                return 0.0;
        }
    }
}