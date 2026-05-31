import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter product to track: ");
        String userInputProduct = scanner.nextLine();
        System.out.print("Enter starting budget: ");
        String userInputBudget = scanner.nextLine();
        System.out.println("Starting JADE Platform\n");
        scanner.close();

        Runtime runtime = Runtime.instance();

        // default profile for main container
        Profile profile = new ProfileImpl();
        // RMA agent
        profile.setParameter(Profile.GUI, "true");

        AgentContainer mainContainer = runtime.createMainContainer(profile);

        try {
            // start the sup agents firfst so they can register with the DF
            int numSuppliers = 3;
            for (int i = 1; i <= numSuppliers; i++) {
                AgentController supplier = mainContainer.createNewAgent("Supplier" + i, "SupplierAgent", null);
                supplier.start();
            }

            // start the Shop Agent with custom parameters
            Object[] shopArguments = new Object[] {
                userInputProduct, // product to track
                "10", // init stock
                "15", // threshold to trigger refill
                "20", // refill quantity
                userInputBudget, // budget
                "6", // max del days
                "0.8", // price weight (80% importance)
                "0.2" // delivery weight (20% importance)
            };

            AgentController shop = mainContainer.createNewAgent("MyShop", "ShopAgent", shopArguments);
            shop.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}