import jade.core.Agent;
import jade.wrapper.AgentController;
import jade.core.Agent;
import jade.wrapper.StaleProxyException;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/*Загрузчик агентов - этот агент должен быть запущен первым из RMA или консоли, считывает файл, в котором лежат количество билетов и сами по содержанию вопросики
 * когда у себя запускать будешь измени пути, как-нибудь изменю нахождение файла, но позже*/
public class AgentsLoaderExt extends Agent {
    @Override
    protected void setup() {
        Object args[] = getArguments();
        int is_questions= Integer.parseInt(args[0].toString());
        // QuestionAgent creation
        BufferedReader reader = null;
        BufferedReader reader_1 = null;
        int lineCount = 0;
        if(is_questions==1) {
            try {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream("C:\\Users\\misha\\IdeaProjects\\Questions&Tickets\\src\\questions.txt"), "utf-8")); // или cp1251

                String currentLine;
                while ((currentLine = reader.readLine()) != null) {
                    lineCount++;
                    //передаем в специальный метод информацию о вопросе для создания агента Вопроса
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
else {
            //ExamCardAgent creation

            int countOfExamCardAgents = 0;
            try {
                reader_1 = new BufferedReader(new InputStreamReader(new FileInputStream("C:\\Users\\misha\\IdeaProjects\\Questions&Tickets\\src\\tickets.txt"), "utf-8")); // или cp1251

                String currentLine;
                if ((currentLine = reader_1.readLine()) != null) {
                    countOfExamCardAgents = Integer.parseInt(currentLine.trim());
                }

            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(AgentsLoader.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(AgentsLoader.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    reader_1.close();
                } catch (IOException ex) {
                    System.out.println("Can't close the file");
                }
            }


            for (int i = 0; i < countOfExamCardAgents; i++) {
                try {
                    //от контроллера контейнера находим класс агента Билета, даём имя и запускаем его
                    AgentController ac = getContainerController().createNewAgent("b" + i, "ExamCardAgent", null);
                    ac.start();
                } catch (StaleProxyException ex) {
                    Logger.getLogger(AgentsLoader.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            //Manager creation


            try
            {
                AgentController ac = getContainerController().createNewAgent("m1", "Manager", null);
                ac.start();
            } catch (StaleProxyException ex)
            {
                Logger.getLogger(AgentsLoader.class.getName()).log(Level.SEVERE, null, ex);
            }
        }







    }

    private AgentController parseAgent(String s) throws StaleProxyException {
        String[] splitted = s.split(";");

        //распиливаем по кусочкам инфу о вопросе и создаем аргументы для скармливания агенту Вопроса
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
                //от контроллера контейнера находим класс агента Вопроса, даём имя и запускаем его
                return getContainerController().createNewAgent(agentName, "QuestionAgent", args);

            default:
                return null;

        }
    }

}