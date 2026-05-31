import java.io.Serial;
import java.io.Serializable;

public class Proposal implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private double price;
    private int deliveryTime;
    private int quantity;
    private String supplierID;

    public Proposal() {
    }

    public Proposal(double price, int deliveryTime, int quantity, String supplierID) {
        this.price = price;
        this.deliveryTime = deliveryTime;
        this.quantity = quantity;
        this.supplierID = supplierID;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getDeliveryTime() {
        return deliveryTime;
    }

    public void setDeliveryTime(int deliveryTime) {
        this.deliveryTime = deliveryTime;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getSupplierID() {
        return supplierID;
    }

    public void setSupplierID(String supplierID) {
        this.supplierID = supplierID;
    }

    @Override
    public String toString() {
        return "Proposal{" +
                "supplierID='" + supplierID + '\'' +
                ", price=" + price +
                ", deliveryTime=" + deliveryTime +
                ", quantity=" + quantity +
                '}';
    }
}