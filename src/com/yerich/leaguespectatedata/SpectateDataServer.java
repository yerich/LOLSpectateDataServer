package com.yerich.leaguespectatedata;

import com.achimala.leaguelib.connection.*;
import com.achimala.leaguelib.models.*;
import com.achimala.leaguelib.errors.*;
import com.achimala.util.Callback;

import java.util.Map;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.io.InputStreamReader;
import java.net.Socket;

public class SpectateDataServer {
	public static void main(String[] args) throws Exception {
		final LeagueConnection c = new LeagueConnection(LeagueServer.NORTH_AMERICA);
		c.getAccountQueue().addAccount(new LeagueAccount(LeagueServer.NORTH_AMERICA, "4.3.xx", "yerichtest1", "rosebudfoo9"));
		
        Map<LeagueAccount, LeagueException> exceptions = c.getAccountQueue().connectAll();
        if(exceptions != null) {
            for(LeagueAccount account : exceptions.keySet())
                System.out.println(account + " error: " + exceptions.get(account));
            return;
        }
		
		ServerSocket listener = new ServerSocket(9898);
		int clientNumber = 0;
		System.out.println("The spectate data server is running.");
	    try {
	        while (true) {
	            new SpectateDataFetcher(listener.accept(), clientNumber++, c).start();
	        }
	    } finally {
	        listener.close();
	    }
	}
	
	/**
	 * A private thread to handle capitalization requests on a particular
	 * socket.  The client terminates the dialogue by sending a single line
	 * containing only a period.
	 */
	private static class SpectateDataFetcher extends Thread {
	    private Socket socket;
	    private int clientNumber;
	    private LeagueConnection connection;
	
	    public SpectateDataFetcher(Socket socket, int clientNumber, LeagueConnection connection) {
	        this.socket = socket;
	        this.clientNumber = clientNumber;
	        this.connection = connection;
	        log("New connection with client# " + clientNumber + " at " + socket);
	    }
	
	    /**
	     * Services this thread's client by first sending the
	     * client a welcome message then repeatedly reading strings
	     * and sending back the capitalized version of the string.
	     */
	    public void run() {
	        try {
	
	            // Decorate the streams so we can send characters
	            // and not just bytes.  Ensure output is flushed
	            // after every newline.
	            BufferedReader in = new BufferedReader(
	                    new InputStreamReader(socket.getInputStream()));
	            final PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
	
	            // Get messages from the client, line by line; return them
	            // capitalized
	            while (true) {
	                String input = in.readLine();
	                String auth = input.trim();
	                if (!auth.equals("Auth: g7H1ag9fB7a4Atqf1F8MzpR25aT5")) {
	                	out.println("{\"status\":\"unauthorized\"}");
	                	break;
	                }
	                out.println("{\"status\":\"ready\"}");
	                
	                input = in.readLine();
	                String lookupName = input.trim();
	                // Get the summoner's data
	                connection.getSummonerService().getSummonerByName(lookupName, new Callback<LeagueSummoner>() {

						@Override
						public void onCompletion(LeagueSummoner summoner) {
							// Got the summoner's data. Now get the game data
			                connection.getGameService().fillActiveGameData(summoner, new Callback<LeagueSummoner>() {
								@Override
								public void onCompletion(LeagueSummoner summoner) {
									log("Getting active game for "+summoner.getName()+".");
									if(summoner.getActiveGame() != null) {
										LeagueGame game = summoner.getActiveGame();
										String encryption_key = game.getObserverCredentials().getEncryptionKey();
										int game_id = game.getId();
										out.println("{\"status\":\"success\",\"active_game\":true,\"game_id\":"+game_id+", \"encryption_key\":\""+encryption_key+"\"}");
									}
									else {
										out.println("{\"status\":\"success\",\"active_game\":false,\"game_id\":0, \"encryption_key\":\"\"}");
									}
								}

								@Override
								public void onError(Exception ex) {
									out.println("{\"status\":\"error\"}");
								}
			                });
						}

						@Override
						public void onError(Exception ex) {
							out.println("{\"status\":\"error\"}");
						}
	                	
	                });
	            }
	        } catch (IOException e) {
	            log("Error handling client# " + clientNumber + ": " + e);
	        } catch (NullPointerException e) {
	        
	        } finally {
	            try {
	                socket.close();
	            } catch (IOException e) {
	                log("Couldn't close a socket, what's going on?");
	            }
	            log("Connection with client# " + clientNumber + " closed");
	        }
	    }
	
	    /**
	     * Logs a simple message.  In this case we just write the
	     * message to the server applications standard output.
	     */
	    private void log(String message) {
	        System.out.println(message);
	    }
	}
}
