/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

/**
 *
 * @author user
 */

public class clientThread extends Thread{
    private String clientName = null;
    private BufferedReader is = null;
    private PrintStream os = null;
    private Socket clientSocket = null;
    private final ConcurrentLinkedQueue<clientThread> threads;
    private final ConcurrentLinkedQueue<clientThread> queue;
    private clientThread opponent;
    private final String date;
    public boolean wasInterrupted = false;

    public clientThread(Socket clientSocket, ConcurrentLinkedQueue<clientThread> threads, ConcurrentLinkedQueue<clientThread> queue, String date) {
        this.clientSocket = clientSocket;
        this.threads = threads;
        this.queue = queue;
        clientName = null;
        opponent = null;
        this.date = date;
    }

    private void instructions(PrintStream os, String name, String op){
        os.println("Every message is required to have a prefix:");
        os.println("|" + name + "|" + op + "|Your message");
        os.println("If you wish to quit the game, begin your message with /quit");
        os.println("|" + name + "|" + op + "|/quit");
    }
    private boolean findOpponent() throws Exception{
        if(opponent != null){
            //opponent has been assigned by another thread
            this.os.println("Opponent Found: 1" + this.opponent.clientName);
            //instructions(os, this.clientName, this.opponent.clientName);
            //instructions(this.opponent.os, this.opponent.clientName, this.clientName);
            return true;
        }
        
        Iterator<clientThread> itr = this.queue.iterator();
        while (itr.hasNext()){
            clientThread challenger = itr.next();
            if(challenger != null && !challenger.equals(this)){
                if (challenger.opponent != null) {
                    throw new Exception("Assignement Inconsistency! Player: " + challenger.getName() + " already has an opponent: " + challenger.getopponent());
                }
                queue.remove(challenger);
                queue.remove(this);
                this.opponent = challenger;
                challenger.opponent = this;
                this.os.println("Opponent Found: 2" + this.opponent.clientName);
                //instructions();

                return true;
            }
        }
        
        //no clients left
        return false;
    }
    
    public String getopponent(){
        if (opponent==null) return null;
        return opponent.getclientName();
    }
    
    public String getclientSocket(){
        if (clientSocket==null) return null;
        return clientSocket.toString();
    }
    
    public String getclientName(){
        return clientName;
    }
    
    public String getdate(){
        return date;
    }
    
    public Socket getSocket(){
        return clientSocket;
    }
    
    /* If the message is valid
     * that is, if it has the following format: |NameSender(this)|NameReceiver(opponent)|move(or /quit)
     */
    private String[] interpretMessage(String input){
        //debug
        System.out.println(input);
        
        if (input.startsWith("|")) {
            String[] msg = input.split("\\|");
            if (msg.length == 4 && msg[1] != null && msg[2] != null && msg[3] != null) {
                msg[1] = msg[1].trim();
                msg[2] = msg[2].trim();
                msg[3] = msg[3].trim();
                if (!msg[1].isEmpty() && !msg[2].isEmpty() && !msg[3].isEmpty()){
                    return msg;
                } else{return null;}
            } else{return null;}
        } else{return null;}
    }
    
    //kill player
    private synchronized void RageQuit(int type){
        if(type==1){
            opponent.os.println("*** The user " + clientName + " is leaving! ***");
            os.println("*** Bye " + clientName + ". GG Easy ***");
            threads.remove(opponent);
            opponent.opponent = null;
            opponent.wasInterrupted = true;
            opponent = null;
        }
        threads.remove(this);
        wasInterrupted = true;
    }
    
    private synchronized void removeFromQueue(){
        if(opponent!=null){
            threads.remove(opponent);
            opponent.opponent = null;
            opponent.wasInterrupted = true;
        }
        threads.remove(this);
        queue.remove(this);
    }
    
    
    public void goodBye(){
        // interrupted by other thread
        os.println("*** Server closed ***");
    }
    
    private boolean validateMove(String input){
        return true;
    }
    
    boolean shouldStop() {
        if(!wasInterrupted)
            wasInterrupted = isInterrupted();
        return wasInterrupted;
    }
    
    /**
     *
     */
    @Override
    public void run() {
        try {
            //Create input and output streams for this client
            is = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            os = new PrintStream(clientSocket.getOutputStream());
            
            //Read name of the client
            String name = null;
            while (!shouldStop() && !clientSocket.isClosed() && (name==null || name.equals(""))) {
                os.println("Keep Alive".toCharArray());sleep(100);
                name = is.readLine().trim();
                if(name.equals("Quit")){
                    RageQuit(0);
                }
            }
            
            //if(name!=null){
            clientName = name;
                //os.println("Welcome " + clientName + "!");
                //os.println("Finding a challenger...");
            //}
            
            int i = 0;
            //Let's find a challenger (the first one avaible to play, i.e., the one that has been waiting the longest)
            if(this.opponent == null){
                while(i<20 && !shouldStop() && !clientSocket.isClosed() && findOpponent()==false) {
                    i=i+1;
                    os.println("Keep Alive".toCharArray());sleep(1000);
                }
            }
            else{//opponent has been assigned by another thread
                this.os.println("Opponent Found: 1" + this.opponent.clientName);
                this.opponent.os.println("Opponent Found: 2" + this.clientName);
                //instructions(os, this.clientName, this.opponent.clientName);
                //instructions(this.opponent.os, this.opponent.clientName, this.clientName);
            }
            
            /* Start the game (exchange of messages) */
            while (i<10 && !shouldStop() && !clientSocket.isClosed()) {
                
                os.println("Keep Alive".toCharArray());sleep(100);
                
                String input = is.readLine(); 
                if(shouldStop()) break;
                if(input.equals("Quit")){
                    RageQuit(1);
                    break;
                }
                
                if(input == null || input.equals("")) continue;
                String[] msg = interpretMessage(input);
                if (validateMove(msg[1])) {
                    opponent.os.println(">" + clientName + "< " + msg[3]);
                    this.os.println("<" + clientName + "> " + msg[3]);

                } else {
                    System.out.println("Invalid message: " + input);
                }
            }
            
            if(i==20){
                os.println("No opponents");
            }
            
            removeFromQueue();
            /*
             * Close the output stream, close the input stream, close the socket.
             */
            is.close();
            os.close();
            if (!clientSocket.isClosed()){
                clientSocket.close();
            }
        }
        catch (InterruptedException e){goodBye();}
        catch (SocketException e) {System.out.println(e.getMessage());
            if(e.getMessage().contains("Broken pipe")){
                opponent.os.println("*** The user " + clientName + " is leaving! ***");
                threads.remove(opponent);
                opponent.opponent = null;
                opponent.wasInterrupted = true;
                opponent = null;
                threads.remove(this);
                wasInterrupted = true;
                try {
                    clientSocket.close();
                } catch (IOException ex) {}
            }
            else{
                System.out.println(e.getMessage());
            }
        }
        catch (NullPointerException e) {System.out.println(e.getMessage());
            if(opponent!=null){
                opponent.os.println("*** The user " + clientName + " is leaving! ***");
                opponent.opponent = null;
                opponent.wasInterrupted = true;
                threads.remove(opponent);
                opponent = null;
            }
            threads.remove(this);
            wasInterrupted = true;
            try {
                clientSocket.close();
            } catch (IOException ex) {
            }
        }
        catch (Exception e) {JOptionPane.showMessageDialog(null, e, "Error", JOptionPane.INFORMATION_MESSAGE);}
    }
}
