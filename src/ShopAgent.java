import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;
import java.util.Vector;
import java.util.Enumeration;

public class ShopAgent extends Agent {

    // Default Inventory parameters
    private int currentStock = 20;
    private int threshold = 10;
    private int targetQuantity = 50;
    private double budget = 10000.00;
    private int maxDeliveryDays = 10;

    // Default Weights for evaluation
    private double priceWeight = 0.7;
    private double deliveryWeight = 0.3;

    private AID[] supplierAgents;
    private boolean isNegotiating = false;

    @Override
    protected void setup() {
        // Parse arguments if provided
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            try {
                if (args.length > 1) currentStock = Integer.parseInt((String) args[0]);
                if (args.length >= 2) threshold = Integer.parseInt((String) args[1]);
                if (args.length >= 3) targetQuantity = Integer.parseInt((String) args[2]);
                if (args.length >= 4) budget = Double.parseDouble((String) args[3]);
                if (args.length >= 5) maxDeliveryDays = Integer.parseInt((String) args[4]);
                if (args.length >= 6) priceWeight = Double.parseDouble((String) args[5]);
                if (args.length >= 7) deliveryWeight = Double.parseDouble((String) args[6]);
                
                System.out.println("Shop-agent " + getAID().getLocalName() + " initialized with CUSTOM parameters.");
            } catch (Exception e) {
                System.err.println("Error parsing arguments for " + getAID().getLocalName() + ". Using defaults.");
            }
        } else {
            System.out.println("Shop-agent " + getAID().getLocalName() + " initialized with DEFAULT parameters.");
        }

        System.out.println("--- CONFIGURATION ---");
        System.out.println("Initial Stock: " + currentStock);
        System.out.println("Threshold: " + threshold);
        System.out.println("Refill Quantity: " + targetQuantity);
        System.out.println("Budget: $" + budget);
        System.out.println("Max Delivery: " + maxDeliveryDays + " days");
        System.out.println("Weights: Price=" + priceWeight + ", Delivery=" + deliveryWeight);
        System.out.println("---------------------");

        discoverSuppliers();

        // Sales Simulator (Every 5 seconds)
        addBehaviour(new SalesSimulator(this, 5000));

        // Periodic Supplier Discovery (Every 30 seconds)
        addBehaviour(new TickerBehaviour(this, 30000) {
            @Override
            protected void onTick() {
                discoverSuppliers();
            }
        });
    }

    private void discoverSuppliers() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("supplying");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            supplierAgents = new AID[result.length];
            for (int i = 0; i < result.length; ++i) {
                supplierAgents[i] = result[i].getName();
            }
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    private class SalesSimulator extends TickerBehaviour {
        public SalesSimulator(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            if (currentStock > 0) {
                int sales = (int) (Math.random() * 5) + 1;
                currentStock = Math.max(0, currentStock - sales);
                System.out.println("\n--- " + getAID().getLocalName() + " SALES ---");
                System.out.println("Items sold: " + sales + " | Remaining Stock: " + currentStock);
            }

            if (currentStock <= threshold && !isNegotiating) {
                initiateProcurement();
            }
        }
    }

    private void initiateProcurement() {
        if (supplierAgents == null || supplierAgents.length == 0) {
            System.out.println(getAID().getLocalName() + ": No suppliers available for restocking.");
            return;
        }

        isNegotiating = true;
        System.out.println(getAID().getLocalName() + ": Stock below threshold (" + threshold + "). Initiating FIPA Contract Net...");

        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
        for (AID agent : supplierAgents) {
            cfp.addReceiver(agent);
        }
        cfp.setProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
        cfp.setContent(String.valueOf(targetQuantity));
        cfp.setReplyByDate(new java.util.Date(System.currentTimeMillis() + 10000));

        addBehaviour(new ContractNetInitiator(this, cfp) {
            @Override
            protected void handleAllResponses(Vector responses, Vector acceptances) {
                ACLMessage bestOffer = null;
                double bestScore = Double.MAX_VALUE;

                Enumeration e = responses.elements();
                while (e.hasMoreElements()) {
                    ACLMessage response = (ACLMessage) e.nextElement();
                    if (response.getPerformative() == ACLMessage.PROPOSE) {
                        try {
                            Proposal p = (Proposal) response.getContentObject();
                            if (p.getDeliveryTime() <= maxDeliveryDays && (p.getPrice() * p.getQuantity()) <= budget) {
                                double score = (p.getPrice() * priceWeight) + (p.getDeliveryTime() * 20.0 * deliveryWeight);
                                if (score < bestScore) {
                                    bestScore = score;
                                    bestOffer = response;
                                }
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }

                e = responses.elements();
                while (e.hasMoreElements()) {
                    ACLMessage response = (ACLMessage) e.nextElement();
                    ACLMessage reply = response.createReply();
                    if (response == bestOffer) {
                        reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                        reply.setContent("Order confirmed.");
                    } else {
                        reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                        reply.setContent("Better offer found.");
                    }
                    acceptances.addElement(reply);
                }

                if (bestOffer != null) {
                    System.out.println(getAID().getLocalName() + ": Accepting proposal from " + bestOffer.getSender().getLocalName());
                } else {
                    System.out.println(getAID().getLocalName() + ": No suitable offers found.");
                    isNegotiating = false;
                }
            }

            @Override
            protected void handleInform(ACLMessage inform) {
                System.out.println(getAID().getLocalName() + ": Received delivery from " + inform.getSender().getLocalName());
                currentStock += targetQuantity;
                System.out.println(getAID().getLocalName() + ": Inventory Updated. New Stock: " + currentStock);
                isNegotiating = false;
            }

            @Override
            protected void handleFailure(ACLMessage failure) {
                System.out.println(getAID().getLocalName() + ": Procurement failed: " + failure.getContent());
                isNegotiating = false;
            }

            protected void handleAllResults(Vector results) {
                isNegotiating = false;
            }
        });
    }

    @Override
    protected void takeDown() {
        System.out.println("Shop-agent " + getAID().getName() + " terminating.");
    }
}