package com.biblio.rest;

import com.biblio.data.Database;
import com.biblio.model.Emprunt;
import com.biblio.model.Livre;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class EtudiantRestServer {

    public static void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // API Endpoints
        server.createContext("/api/auth", new AuthHandler());
        server.createContext("/api/livres", new LivreHandler());
        server.createContext("/api/emprunts", new EmpruntHandler());
        server.createContext("/api/return", new ReturnHandler());
        server.createContext("/api/change-password", new ChangePasswordHandler());
        server.createContext("/api/profile", new ProfileHandler());
        server.createContext("/api/reservations", new ReservationHandler());
        server.createContext("/api/penalites", new PenaliteHandler());
        server.createContext("/api/notifications", new NotificationHandler());

        // Serveur de fichiers statiques (HTML/CSS/JS)
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("✅ WEB Server (Etudiant) démarré sur : http://localhost:" + port);
    }

    // --- Serveur de Fichiers (Pour lire le HTML) ---
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            
            // On cherche les fichiers dans src/main/resources/web
            File file = new File("src/main/resources/web" + path);
            
            if (file.exists()) {
                String mime = "text/html";
                if(path.endsWith(".css")) mime = "text/css";
                else if(path.endsWith(".js")) mime = "application/javascript";

                exchange.getResponseHeaders().set("Content-Type", mime);
                exchange.sendResponseHeaders(200, file.length());
                OutputStream os = exchange.getResponseBody();
                Files.copy(file.toPath(), os);
                os.close();
            } else {
                String msg = "404 Not Found";
                exchange.sendResponseHeaders(404, msg.length());
                exchange.getResponseBody().write(msg.getBytes());
                exchange.close();
            }
        }
    }

    // --- API AUTH ---
    static class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (addCorsHeaders(exchange)) return;
            if ("POST".equals(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery(); // cin=...&pass=...
                String cin = getParameter(query, "cin");
                String pass = getParameter(query, "pass");
                
                int id = Database.checkLogin(cin, pass);
                Map<String, Object> resp = new HashMap<>();
                if(id != -1) {
                    resp.put("success", true);
                    resp.put("id", id);
                } else {
                    resp.put("success", false);
                }
                sendJson(exchange, resp);
            }
        }
    }

    // --- API LIVRES ---
    static class LivreHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (addCorsHeaders(exchange)) return;
            if ("GET".equals(exchange.getRequestMethod())) {
                List<Livre> result = Database.getLivres();
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.contains("q=")) {
                    String keyword = getParameter(query, "q").toLowerCase();
                    result = result.stream()
                            .filter(l -> l.getTitre().toLowerCase().contains(keyword) 
                                      || l.getAuteur().toLowerCase().contains(keyword)
                                      || l.getCategorie().toLowerCase().contains(keyword))
                            .collect(Collectors.toList());
                }
                sendJson(exchange, result);
            }
        }
    }

    // --- API EMPRUNTS ---
    static class EmpruntHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (addCorsHeaders(exchange)) return;
            String method = exchange.getRequestMethod();
            String query = exchange.getRequestURI().getQuery();

            if ("POST".equals(method)) {
                int etudiantId = Integer.parseInt(getParameter(query, "etudiantId"));
                int livreId = Integer.parseInt(getParameter(query, "livreId"));
                
                // Vérifier si l'étudiant a déjà ce livre en cours d'emprunt
                boolean dejaEmprunte = Database.getEmpruntsByEtudiant(etudiantId).stream()
                        .anyMatch(e -> e.getLivreId() == livreId && e.isActif());
                
                if (dejaEmprunte) {
                    sendJson(exchange, Map.of("success", false, "message", "Vous avez déjà emprunté ce livre."));
                    return;
                }

                // Vérifier si dispo
                Optional<Livre> l = Database.getLivres().stream().filter(liv -> liv.getId() == livreId && liv.isDisponible()).findFirst();
                if(l.isPresent()) {
                    Database.creerEmprunt(livreId, etudiantId, LocalDate.now().toString());
                    sendJson(exchange, Map.of("success", true, "message", "Livre emprunté avec succès"));
                } else {
                    sendJson(exchange, Map.of("success", false, "message", "Livre indisponible"));
                }
            } else if ("GET".equals(method)) {
                int etudiantId = Integer.parseInt(getParameter(query, "etudiantId"));
                sendJson(exchange, Database.getEmpruntsByEtudiant(etudiantId));
            }
        }
    }
    
    // --- API PÉNALITÉS ---
    static class PenaliteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (addCorsHeaders(exchange)) return;
            int etudiantId = Integer.parseInt(getParameter(exchange.getRequestURI().getQuery(), "etudiantId"));
            sendJson(exchange, Database.getPenalites(etudiantId));
        }
    }

    // --- API NOTIFICATIONS RÉSERVATIONS ---
    static class NotificationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (addCorsHeaders(exchange)) return;
            if ("GET".equals(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                int etudiantId = Integer.parseInt(getParameter(query, "etudiantId"));
                
                // Vérifier si l'étudiant a des réservations notifiées
                List<String> notifications = new ArrayList<>();
                try (Connection conn = Database.getConnection(); PreparedStatement p = conn.prepareStatement(
                        "SELECT l.titre FROM reservations r JOIN livres l ON r.livre_id=l.id WHERE r.etudiant_id=? AND r.statut='NOTIFIED'")) {
                    p.setInt(1, etudiantId);
                    ResultSet rs = p.executeQuery();
                    while (rs.next()) {
                        notifications.add("📚 Le livre '" + rs.getString("titre") + "' est maintenant disponible !");
                    }
                } catch (SQLException e) {}
                
                sendJson(exchange, notifications);
            }
        }
    }

    // --- API RÉSERVATIONS ---
    static class ReservationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (addCorsHeaders(exchange)) return;
            String method = exchange.getRequestMethod();
            if ("POST".equals(method)) {
                String q = exchange.getRequestURI().getQuery();
                int etudiantId = Integer.parseInt(getParameter(q, "etudiantId"));
                int livreId = Integer.parseInt(getParameter(q, "livreId"));
                Database.reserverLivre(livreId, etudiantId);
                sendJson(exchange, "Réservation enregistrée");
            } else if ("DELETE".equals(method)) {
                String q = exchange.getRequestURI().getQuery();
                int reservationId = Integer.parseInt(getParameter(q, "reservationId"));
                Database.deleteReservation(reservationId);
                sendJson(exchange, "Réservation supprimée");
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // Handler pour retours côté étudiant (POST)
    static class ReturnHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (addCorsHeaders(exchange)) return;
            if ("POST".equals(exchange.getRequestMethod())) {
                String q = exchange.getRequestURI().getQuery();
                int etudiantId = Integer.parseInt(getParameter(q, "etudiantId"));
                int livreId = Integer.parseInt(getParameter(q, "livreId"));
                System.out.println("Demande de retour : etudiant=" + etudiantId + ", livre=" + livreId);

                // Rechercher l'emprunt actif pour cet étudiant et ce livre
                boolean found = false;
                for (Emprunt e : Database.getEmpruntsByEtudiant(etudiantId)) {
                    if (e.getLivreId() == livreId && e.isActif()) {
                        // Mark return requested; admin will validate
                        boolean success = Database.requestReturn(e.getId());
                        if (success) {
                            sendJson(exchange, Map.of("success", true, "message", "Retour demandé. En attente de validation."));
                        } else {
                            sendJson(exchange, Map.of("success", false, "message", "Erreur lors de la demande de retour."));
                        }
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    sendJson(exchange, Map.of("success", false, "message", "Aucun emprunt actif trouvé pour ce livre."));
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // Handler profil étudiant (GET ?etudiantId=)
    static class ProfileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("GET".equals(exchange.getRequestMethod())) {
                String q = exchange.getRequestURI().getQuery();
                int etudiantId = Integer.parseInt(getParameter(q, "etudiantId"));
                String[] s = Database.getEtudiantById(etudiantId);
                if (s != null) {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("id", Integer.parseInt(s[0]));
                    resp.put("nom", s[1]);
                    resp.put("email", s[2]);
                    sendJson(exchange, resp);
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- CHANGE PASSWORD ---
    static class ChangePasswordHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("POST".equals(exchange.getRequestMethod())) {
                String q = exchange.getRequestURI().getQuery();
                int etudiantId = Integer.parseInt(getParameter(q, "etudiantId"));
                String oldPass = getParameter(q, "oldPass");
                String newPass = getParameter(q, "newPass");
                boolean ok = Database.changePassword(etudiantId, oldPass, newPass);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", ok);
                sendJson(exchange, resp);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // --- UTILITAIRES ---
    private static void sendJson(HttpExchange ex, Object obj) throws IOException {
        String json = new Gson().toJson(obj);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, json.getBytes().length);
        OutputStream os = ex.getResponseBody();
        os.write(json.getBytes());
        os.close();
    }
    
    private static boolean addCorsHeaders(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS, DELETE");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Accept");
        // Handle preflight
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return true;
        }
        return false;
    }

    private static String getParameter(String query, String param) {
        if(query == null) return "";
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length > 1 && kv[0].equals(param)) return kv[1];
        }
        return "";
    }
}