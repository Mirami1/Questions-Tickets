import jade.core.Agent;
import jade.wrapper.AgentController;
import jade.core.Agent;
import jade.wrapper.StaleProxyException;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AgentsLoader extends Agent {
    @Override
    protected void setup() {
        // QuestionAgent creation
        BufferedReader reader = null;
        BufferedReader reader_1 = null;
        int lineCount = 0;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream("C:\\Users\\misha\\IdeaProjects\\Questions&Tickets\\src\\agents.txt"), "utf-8")); // или cp1251

            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                lineCount++;
                AgentController ac = parseAgent(currentLine);
                if (ac != null) {
                    ac.start();
                }

            }
        } catch (IOException ex) {
            System.out.println("Reading error in line " + lineCount);
        } catch (StaleProxyException ex) {
            Logger.getLogger(AgentsLoader.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                reader.close();
            } catch (IOException ex) {
                System.out.println("Can't close the file");
            }
        }

        //ExamCardAgent creation

        int countOfExamCardAgents = 0;
        try {
            reader_1 = new BufferedReader(new InputStreamReader(new FileInputStream("C:\\Users\\misha\\IdeaProjects\\Questions&Tickets\\src\\agents.txt"), "utf-8")); // или cp1251

            String currentLine;
            if ((currentLine = reader_1.readLine()) != null) {
                countOfExamCardAgents = Integer.parseInt(currentLine.trim());
            }

        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(AgentsLoader.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(AgentsLoader.class.getName()).log(Level.SEVERE, null, ex);
        }
        finally {
            try {
                reader_1.close();
            } catch (IOException ex) {
                System.out.println("Can't close the file");
            }
        }


        for(int i=0;i<countOfExamCardAgents;i++){
            try {
                AgentController ac = getContainerController().createNewAgent("b"+i,"ExamCardAgent",null);
                ac.start();
            } catch (StaleProxyException ex) {
                Logger.getLogger(AgentsLoader.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        //Manager creation
        //TODO Manager

       /* try
        {
            AgentController ac = getContainerController().createNewAgent("m1", "Manager", null);
            ac.start();
        } catch (StaleProxyException ex)
        {
            Logger.getLogger(AgentsLoader.class.getName()).log(Level.SEVERE, null, ex);
        }*/





    }

    private AgentController parseAgent(String s) throws StaleProxyException {
        String[] splitted = s.split(";");

        switch (splitted[0].charAt(0)) {
            case 'q':
                String agentName = splitted[0] + splitted[1];

                String theme = splitted[1];
                String text = splitted[2];
                int complexity = Integer.parseInt(splitted[3]);

                Object[] args = new Object[]
                        {
                                theme, text, complexity
                        };

                return getContainerController().createNewAgent(agentName, "QuestionAgent", args);

            default:
                return null;

        }
    }

}