public class Calculator {
    // Basic arithmetic operations
    
    /**
     * Adds two integers
     */
    public int add(int a, int b) {
        return a + b;
    }
    
    /**
     * Subtracts second number from first
     */
    public int subtract(int a, int b) {
        return a - b;
    }
    
    /**
     * Multiplies two integers
     */
    public int multiply(int a, int b) {
        return a * b;
    }
    
    /**
     * Divides two integers
     */
    public int divide(int a, int b) {
        if (b == 0) {
            throw new ArithmeticException("Cannot divide by zero");
        }
        return a / b;
    }
    
    public static void main(String[] args) {
        Calculator calc = new Calculator();
        int sum = calc.add(10, 5);
        int difference = calc.subtract(10, 5);
        System.out.println("Sum: " + sum);
        System.out.println("Difference: " + difference);
    }
}