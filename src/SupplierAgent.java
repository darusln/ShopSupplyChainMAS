import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ContractNetResponder;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAAgentManagement.FailureException;

public class SupplierAgent extends Agent {

    private double basePrice;
    private double currentPrice;
    private int deliveryTime;
    private int stock;
    private int maxStock = 200;

    @Override
    protected void setup() {
        System.out.println("Supplier agent " + getAID().getName() + " is ready.");

        // randomize initial values to create variety among suppliers
        this.stock = 100 + (int)(Math.random() * 50);
        this.basePrice = 80.0 + (Math.random() * 70.0);
        this.currentPrice = Math.round(this.basePrice * 100.0) / 100.0;
        this.deliveryTime = 1 + (int)(Math.random() * 5);

        System.out.println(getAID().getLocalName() + " Config -> Stock: " + stock + ", Base Price: " + currentPrice + ", Delivery: " + deliveryTime);
        // register supplier service in yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("supplying");
        sd.setName("JADE-procurement-supplier");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        MessageTemplate template = ContractNetResponder.createMessageTemplate(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
        addBehaviour(new ContractNetResponder(this, template) {
            @Override
            protected ACLMessage handleCfp(ACLMessage cfp) throws NotUnderstoodException, RefuseException {
                String[] requestParts = cfp.getContent().split(",");
                String requestedProduct = requestParts[0];
                int requestedQuantity = Integer.parseInt(requestParts[1]);

                if (requestedQuantity <= stock) {
                    ACLMessage propose = cfp.createReply();
                    propose.setPerformative(ACLMessage.PROPOSE);
                    try {
                        Proposal offer = new Proposal(currentPrice, deliveryTime, requestedQuantity, getLocalName());
                        propose.setContentObject(offer);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    System.out.println(getLocalName() + ": Received CFP for " + requestedQuantity + " units of " + requestedProduct);
                    return propose;
                } else {
                    throw new RefuseException("insufficient-stock");
                }
            }

            @Override
            protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {
                System.out.println(getLocalName() + ": Proposal ACCEPTED by " + accept.getSender().getLocalName());
                try {
                    Proposal p = (Proposal) propose.getContentObject();
                    if (p.getQuantity() <= stock) {
                        stock -= p.getQuantity();
                        ACLMessage inform = accept.createReply();
                        inform.setPerformative(ACLMessage.INFORM);
                        inform.setContent("Order delivered.");
                        return inform;
                    } else {
                        throw new FailureException("stock-exhausted");
                    }
                } catch (Exception e) {
                    throw new FailureException("decoding-error");
                }
            }

            @Override
            protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
                System.out.println(getLocalName() + ": Proposal REJECTED by " + reject.getSender().getLocalName());
            }
        });

        // Behavior to restock periodically
        addBehaviour(new TickerBehaviour(this, 10000) {
            @Override
            protected void onTick() {
                // restock inventory
                if (stock < maxStock) {
                    int restockAmount = 10 + (int)(Math.random() * 10);
                    stock = Math.min(maxStock, stock + restockAmount);
                }

                // improve competition by fluctuating prices slightly over time
                // alter price by +/- 5% to allow different suppliers to win
                double fluctuation = 0.95 + (Math.random() * 0.10);
                currentPrice = Math.round(basePrice * fluctuation * 100.0) / 100.0;
            }
        });
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Supplier agent " + getAID().getName() + " terminating.");
    }
}