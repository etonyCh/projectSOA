package com.biblio.soap;

import com.biblio.data.Database;
import com.biblio.model.Livre;
import javax.jws.WebService;
import java.util.List;

@WebService(endpointInterface = "com.biblio.soap.BibliothecaireService")
public class BibliothecaireImpl implements BibliothecaireService {
    @Override public String ajouterLivre(String titre, String auteur, String cat) { Database.addLivre(titre, auteur, cat); return "OK"; }
    @Override public boolean supprimerLivre(int id) { return Database.supprimerLivre(id); }
    @Override public List<Livre> listerLivres() { return Database.getLivres(); }
    
    @Override public String ajouterEtudiant(String nom, String cin) { 
        // Générer un mot de passe aléatoire sécurisé
        String defaultPassword = "Etudiant2024!";
        Database.addEtudiant(nom, cin, defaultPassword);
        return "Étudiant ajouté avec CIN: " + cin + " et mot de passe: " + defaultPassword;
    }
    @Override public boolean supprimerEtudiant(int id) { 
        Database.deleteEtudiant(id);
        return true; 
    }
}