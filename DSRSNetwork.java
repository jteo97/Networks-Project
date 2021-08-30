import java.net.*;
import java.io.*;
import java.util.*;
import java.time.*;

public class DSRSNetwork {

    private static final String hostname = "127.0.0.1";
    private static final String droneName = "Relay1"; //change to Relay1 when submitting
    private static int[][] dvTable;
    private static String[] dvTableHelper;
    private static final String forwardingFileName = "forwarding-Relay1.csv";
    private static String[] forwardingTable;
    private static List<String> relaysToSendTo = new ArrayList<String>();

    public static void main(String[] args) {
        task1();
        task2();
    }

    private static void task1() {
        //Task1
        String clientName = "";

        try {
            System.out.println("Starting ping process #1");
            System.out.println("Reading client list: starting");
            File clientInfo = new File("clients-Relay1.csv");
            Scanner myReader = new Scanner(clientInfo);
            String outputFileName = "clients-" + droneName + ".csv";
            List<String> clientListIn = new ArrayList<String>();
            List<String> clientListOut = new ArrayList<String>();

            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                if (!data.split(",")[0].equals(droneName)) {
                    clientListIn.add(data);
                }
            }

            myReader.close();
            System.out.println("Reading client list: finished - " + clientListIn.size() + " clients read");
            dvTableHelper = new String[clientListIn.size() + 1];
            dvTableHelper[0] = "Relay1";
            forwardingTable = new String[dvTableHelper.length];
            forwardingTable[0] = "";
            dvTable = new int[dvTableHelper.length][dvTableHelper.length];


            for (int i = 0; i < dvTable.length; i++) {
                for (int j = 0; j < dvTable.length; j++) {
                    dvTable[i][j] = -1;
                    if (i==j) {
                        dvTable[i][j] = 0;
                    }
                }
            }

            dvTable[0][0] = 0;
            System.out.println("Pinging all clients: starting");
            int count = 1;
            for (String data: clientListIn) {
                List<String> dataList = Arrays.asList(data.split(","));
                clientName = dataList.get(0);
                String clientType = dataList.get(1);
                String temp = dataList.get(2);
                String clientIP = temp.substring(0, temp.indexOf(":"));
                String clientPort = temp.substring(temp.indexOf(":")+1);
                String clientLastResponse = dataList.get(3);

                if (clientName.equals(droneName)) {
                    temp = String.join(",", dataList);
                    clientListOut.add(temp);

                } else {
                    dvTableHelper[count] = clientName;
                    try {
                        Socket socket = new Socket(hostname, Integer.parseInt(clientPort));

                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        String msg = "PING\n";
                        Instant start = Instant.now();
                        out.writeUTF(msg);

                        DataInputStream input = new DataInputStream(socket.getInputStream());

                        String rcvMsg = input.readUTF();
                        Instant end = Instant.now();

                        Duration timeTaken = Duration.between(start, end);
                        System.out.println("- Pinging " + clientName + "...ping received after " + timeTaken.getSeconds() + "s");
                        dataList.set(3, Long.toString(timeTaken.getSeconds()));
                        String forward = dataList.get(0) + "," + dataList.get(0);
                        temp = String.join(",", dataList);
                        clientListOut.add(temp);
                        forwardingTable[count] = forward;
                        dvTable[0][count] = Integer.parseInt(dataList.get(3));
                        count++;

                        if (clientType.equals("Relay")) {
                            relaysToSendTo.add(data);
                        }

                    } catch (IOException e) {
                        System.out.println("- Pinging " + clientName + "...could not ping");
                        dataList.set(3, "-1");
                        temp = String.join(",", dataList);
                        clientListOut.add(temp);
                        forwardingTable[count] = "";
                        dvTable[0][count] = Integer.parseInt(dataList.get(3));
                        count++;
                    }
                }
            }

            System.out.println("Pinging all clients: finished - " + clientListIn.size() + " clients pinged");
            System.out.println("Writing client list: started");

            FileWriter myWriter = new FileWriter(outputFileName);
            for (String data: clientListOut) {
                myWriter.write(data + "\n");
            }
            myWriter.close();

            FileWriter myWriter1 = new FileWriter(forwardingFileName);
            for (String data: forwardingTable) {
                myWriter1.write(data + "\n");
            }
            myWriter1.close();

            System.out.println("Writing client list: finished - " + clientListIn.size() + " clients written");
            System.out.println("Ping process #1 finished");

        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void task2() {
        //Task2
        int localPort = 10120;



        while(true) {
            try {
                ServerSocket serverSocket = new ServerSocket(localPort);


                Socket socket = serverSocket.accept();
                DataInputStream input = new DataInputStream(socket.getInputStream());

                String rcvMsg = "";
                if (!(rcvMsg = input.readUTF()).contains("UPDATE")) {

                } else {
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    String msg = "ACK\n";
                    out.writeUTF(msg);
                    System.out.println("New DVs received");
                    System.out.println("Starting DV update calculation");
                    String[] msgComponents = rcvMsg.split(":");
                    String incomingDrone = msgComponents[1];
                    List<String> dvUpdates = Arrays.asList(msgComponents[2].split(","));

                    for (int i = 0; i < dvTableHelper.length; i++) {
                        for (String data: dvUpdates) {
                            String[] temp = data.split("=");
                            if (dvTableHelper[i].equals(incomingDrone)) {
                                for (int j = 0; j < dvTableHelper.length; j++) {
                                    if (dvTableHelper[j].equals(temp[0])) {
                                        dvTable[i][j]= Integer.parseInt(temp[1]);
                                    }
                                }
                            }
                        }
                    }

                    List<Integer> listOfDronesUpdated = new ArrayList<Integer>();

                    for (int i = 1; i < dvTable[0].length; i++) {
                        System.out.print("Calculating cost for " + dvTableHelper[i] + "...");
                        int oldValue = dvTable[0][i];

                        List<Integer> calculatedCosts = new ArrayList<Integer>();
                        calculatedCosts.add(-1);

                        for (int j = 1; j < dvTable.length; j++) {
                            int costToMe = dvTable[0][j];

                            int newValue = dvTable[j][i];

                            if (costToMe == -1 || newValue == -1) {
                                calculatedCosts.add(-1);
                            } else {
                                calculatedCosts.add(costToMe + newValue);
                            }
                        }

                        int minValue = Integer.MAX_VALUE;
                        int minValueIndex = -1;
                        for (int k = 0; k < calculatedCosts.size(); k++) {
                            if (calculatedCosts.get(k) > -1 && calculatedCosts.get(k) < minValue) {
                                minValue = calculatedCosts.get(k);
                                minValueIndex = k;
                            }
                        }

                        if (minValueIndex == -1) {
                            if (dvTable[0][i] == -1) {
                                System.out.println("no change");
                                continue;
                            } else {
                                dvTable[0][i] = -1;
                                forwardingTable[i] = "";
                                listOfDronesUpdated.add(i);
                                System.out.println("cost updated to " + -1);
                                continue;
                            }
                        }

                        minValue = calculatedCosts.get(minValueIndex);

                        if (oldValue == minValue) {
                            System.out.println("no change");
                        } else {
                            dvTable[0][i] = minValue;
                            forwardingTable[i] = dvTableHelper[i] + "," + dvTableHelper[minValueIndex];
                            listOfDronesUpdated.add(i);
                            System.out.println("cost updated to " + minValue + " via " + dvTableHelper[minValueIndex]);
                        }
                    }

                    if (listOfDronesUpdated.size() > 0) {
                        System.out.println("Sending updated DVs");
                        for (String relayData: relaysToSendTo) {
                            String[] relayDataSplit = relayData.split(",");
                            msg = "UPDATE:" + droneName + ":";

                            int count = 1;
                            for (int i = 0; i < listOfDronesUpdated.size(); i++) {
                                if (count>1) {
                                    msg += ",";
                                }
                                msg += dvTableHelper[listOfDronesUpdated.get(i)] + "=" + dvTable[0][listOfDronesUpdated.get(i)];
                                count++;
                            }

                            msg += ":" + listOfDronesUpdated.size() + "\n";

                            Socket socket1 = new Socket(hostname, Integer.parseInt(relayDataSplit[2].substring(relayDataSplit[2].indexOf(":") + 1)));


                            out = new DataOutputStream(socket1.getOutputStream());
                            out.writeUTF(msg);
                            System.out.println("- Sending to " + relayDataSplit[0] + "...done");
                        }
                    } else {
                        System.out.println("Skipping DV update send");
                    }

                    System.out.println("DV update calculation finished");

                    FileWriter myWriter = new FileWriter(forwardingFileName);
                    for (int i = 0; i < forwardingTable.length; i++) {
                        myWriter.write(forwardingTable[i] + "\n");
                    }
                    myWriter.close();
                }

                socket.close();
                serverSocket.close();
            } catch (IOException e) {

            }

        }

    }
}