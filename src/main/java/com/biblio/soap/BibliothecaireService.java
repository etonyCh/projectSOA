package com.biblio.soap;

import com.biblio.model.Livre;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import java.util.List;

@WebService
public interface BibliothecaireService {
    @WebMethod String ajouterLivre(@WebParam(name = "titre") String titre, @WebParam(name = "auteur") String auteur, @WebParam(name = "cat") String cat);
    @WebMethod boolean supprimerLivre(@WebParam(name = "id") int id);
    @WebMethod List<Livre> listerLivres();
    
    // Nouvelles fonctionnalités exposées
    @WebMethod String ajouterEtudiant(@WebParam(name="nom") String nom, @WebParam(name="cin") String cin);
    @WebMethod boolean supprimerEtudiant(@WebParam(name="id") int id);
    // Note: SOAP gère mal les List<String[]>, on reste simple pour l'exemple scolaire
}