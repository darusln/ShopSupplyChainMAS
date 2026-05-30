import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

public class Main {
    public static void main(String[] args) {
        // 1. Get the JADE Runtime instance
        Runtime runtime = Runtime.instance();

        // 2. Create a default profile for the Main Container
        Profile profile = new ProfileImpl();

        // Optional: Show the JADE GUI (the RMA agent)
        profile.setParameter(Profile.GUI, "true");

        // 3. Create the Main Container
        AgentContainer mainContainer = runtime.createMainContainer(profile);

        try {
            // 4. Start the Supplier Agents FIRST so they can register with the DF
            AgentController supplier1 = mainContainer.createNewAgent("SupplierA", "SupplierAgent", null);
            AgentController supplier2 = mainContainer.createNewAgent("SupplierB", "SupplierAgent", null);
            AgentController supplier3 = mainContainer.createNewAgent("SupplierC", "SupplierAgent", null);

            supplier1.start();
            supplier2.start();
            supplier3.start();

            // Give the suppliers a brief moment to register their services
            Thread.sleep(1000);

            // 5. Start the Shop Agent with CUSTOM parameters
            // Parameters: {InitialStock, Threshold, RefillQuantity, Budget, MaxDeliveryDays, PriceWeight, DeliveryWeight}
            Object[] shopArguments = new Object[] {
                "10",      // Initial Stock
                "15",      // Threshold (reorder when stock <= 15)
                "20",      // Refill Quantity
                "7000", // Budget
                "6",       // Max Delivery Days
                "0.8",     // Price Weight (80% importance)
                "0.2"      // Delivery Weight (20% importance)
            };

            AgentController shop = mainContainer.createNewAgent("MyShop", "ShopAgent", shopArguments);
            shop.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}