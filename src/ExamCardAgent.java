import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import java.util.HashSet;

public class ExamCardAgent extends Agent {
    private Question firstQuestion;
    private Question secondQuestion;
    public double average = 0;
    boolean isChanged = false;

    HashSet<AID> simpleCards;

    protected void setup()
    {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("card");
        sd.setName("MyCard");
        dfd.addServices(sd);
        try
        {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }
        //addBehaviour();
        System.out.println(this.getLocalName() + "создан");
    }

}

