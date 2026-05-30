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

    private double price;
    private int deliveryTime;
    private int stock;
    private int maxStock = 200;

    @Override
    protected void setup() {
        System.out.println("Supplier-agent " + getAID().getName() + " is ready.");

        // Randomize initial values to create variety among suppliers
        this.stock = 100 + (int)(Math.random() * 50);
        this.price = 80.0 + (Math.random() * 70.0); 
        this.price = Math.round(this.price * 100.0) / 100.0;
        this.deliveryTime = 1 + (int)(Math.random() * 5); 

        System.out.println(getAID().getLocalName() + " Config -> Stock: " + stock + ", Price: " + price + ", Delivery: " + deliveryTime);

        // Register supplier service in yellow pages
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

        // Add the FIPA Contract Net Responder behavior
        MessageTemplate template = ContractNetResponder.createMessageTemplate(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
        addBehaviour(new ContractNetResponder(this, template) {
            @Override
            protected ACLMessage handleCfp(ACLMessage cfp) throws NotUnderstoodException, RefuseException {
                System.out.println(getLocalName() + ": Received CFP from " + cfp.getSender().getLocalName());
                int requestedQuantity = Integer.parseInt(cfp.getContent());

                if (requestedQuantity <= stock) {
                    // We can provide the requested quantity
                    ACLMessage propose = cfp.createReply();
                    propose.setPerformative(ACLMessage.PROPOSE);
                    try {
                        Proposal offer = new Proposal(price, deliveryTime, requestedQuantity, getLocalName());
                        propose.setContentObject(offer);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    System.out.println(getLocalName() + ": Proposing price " + price + " for " + requestedQuantity + " units.");
                    return propose;
                } else {
                    // Refuse if not enough stock
                    System.out.println(getLocalName() + ": Refusing CFP (Low stock: " + stock + ")");
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
                        System.out.println(getLocalName() + ": Order processed. Remaining Stock: " + stock);
                        ACLMessage inform = accept.createReply();
                        inform.setPerformative(ACLMessage.INFORM);
                        inform.setContent("Order delivered.");
                        return inform;
                    } else {
                        System.out.println(getLocalName() + ": Delivery failure (Stock ran out since proposal).");
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
                if (stock < maxStock) {
                    int restockAmount = 10 + (int)(Math.random() * 10);
                    stock = Math.min(maxStock, stock + restockAmount);
                }
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
        System.out.println("Supplier-agent " + getAID().getName() + " terminating.");
    }
}