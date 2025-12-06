package org.example.handler;

public class OrderStatusHandler {
    
    public String getStatusMessage(String status) {
        if (status.equals("PENDING")) {
            return "Order is pending processing";
        } else if (status.equals("CONFIRMED")) {
            return "Order has been confirmed";
        } else if (status.equals("SHIPPED")) {
            return "Order has been shipped";
        } else if (status.equals("DELIVERED")) {
            return "Order has been delivered";
        } else if (status.equals("CANCELLED")) {
            return "Order has been cancelled";
        } else if (status.equals("REFUNDED")) {
            return "Order has been refunded";
        } else {
            return "Unknown order status";
        }
    }
    
    public int getProcessingTime(String priority) {
        if (priority.equals("HIGH")) {
            return 1;
        } else if (priority.equals("MEDIUM")) {
            return 3;
        } else if (priority.equals("LOW")) {
            return 7;
        } else {
            return 5;
        }
    }
    
    public double getShippingCost(String method) {
        if (method.equals("EXPRESS")) {
            return 29.99;
        } else if (method.equals("STANDARD")) {
            return 9.99;
        } else if (method.equals("ECONOMY")) {
            return 4.99;
        } else {
            return 0.0;
        }
    }
}