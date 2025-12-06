package com.biblio;

import com.biblio.data.Database;
import com.biblio.gui.BibliothequeGUI;
import com.biblio.rest.EtudiantRestServer;
import com.biblio.soap.BibliothecaireImpl;

import javax.swing.*;
import javax.xml.ws.Endpoint;

public class MainApp {
    public static void main(String[] args) {
        try {
            // 1. Initialiser la BD (Création des tables)
            Database.initialize();
            
            // 2. Démarrer SOAP (Admin Web Service)
            String urlSoap = "http://localhost:9090/ws/biblio";
            Endpoint.publish(urlSoap, new BibliothecaireImpl());
            System.out.println("✅ SOAP Server démarré : " + urlSoap + "?wsdl");

            // 3. Démarrer REST (Etudiant API)
            EtudiantRestServer.start(9091);

            // 4. Lancer l'Interface Graphique (Swing)
            SwingUtilities.invokeLater(() -> {
                new BibliothequeGUI().setVisible(true);
            });
            
            System.out.println("🖥️ Interface graphique lancée.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}