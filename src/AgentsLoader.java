import jade.core.Agent;
import jade.wrapper.AgentController;
import jade.core.Agent;
import jade.wrapper.StaleProxyException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AgentsLoader extends Agent {
    @Override
    protected void setup() {
        // QuestionAgent creation
        BufferedReader reader = null;
        int lineCount = 0;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream("agents.txt"), "utf-8")); // или cp1251
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

                return getContainerController().createNewAgent(agentName, "Questions&Tickets.QuestionAgent", args);

            default:
                return null;

        }
    }

}