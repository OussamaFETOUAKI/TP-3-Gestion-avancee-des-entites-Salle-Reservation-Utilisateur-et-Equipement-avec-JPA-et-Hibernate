package com.example;

import com.example.model.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.time.LocalDateTime;
import java.util.List;

public class App {

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("  TP 3 : Gestion avancée des entités Salle, Réservation, Utilisateur & Équipement");
        System.out.println("         avec JPA et Hibernate (Architecture JEE sans EJB)");
        System.out.println("===============================================================================\n");

        EntityManagerFactory emf = Persistence.createEntityManagerFactory("gestion-reservations-pu");
        EntityManager em = emf.createEntityManager();

        try {
            // -------------------------------------------------------------------------
            // STEP 9 : Création & Persistance avec Cascade (PERSIST)
            // -------------------------------------------------------------------------
            System.out.println(">>> [ÉTAPE 1] Création et Persistance du graphe d'entités avec Cascade (PERSIST)...");
            em.getTransaction().begin();

            // Création de l'utilisateur
            Utilisateur user = new Utilisateur();
            user.setEmail("alice@example.com");
            user.setNom("Alice Dupont");

            // Création de la salle
            Salle salle = new Salle();
            salle.setNom("Salle A");
            salle.setCapacite(30);

            // Création d'équipements
            Equipement projecteur = new Equipement("Projecteur 4K");
            Equipement tableau = new Equipement("Tableau Blanc Interactif");

            // Association ManyToMany Salle <-> Équipements
            salle.addEquipement(projecteur);
            salle.addEquipement(tableau);

            // Création d'une réservation
            Reservation reservation = new Reservation();
            reservation.setDateDebut(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0));
            reservation.setDateFin(LocalDateTime.now().plusDays(1).withHour(11).withMinute(0));
            reservation.setMotif("Réunion Revue de Sprint Projet JPA");
            reservation.setSalle(salle);
            reservation.setUtilisateur(user);

            // Association OneToMany Utilisateur -> Réservation
            user.addReservation(reservation);

            // La persistance de l'utilisateur propage la persistance vers Reservation, Salle et Équipements grâce aux cascades
            em.persist(user);

            em.getTransaction().commit();
            System.out.println("✔ [SUCCÈS] Utilisateur, Réservation, Salle et Équipements persistés avec succès via Cascade!\n");

            // -------------------------------------------------------------------------
            // Consultation et vérification de la persistance
            // -------------------------------------------------------------------------
            System.out.println(">>> [ÉTAPE 2] Consultation des données insérées en base H2...");
            em.clear(); // Vider le cache de premier niveau pour forcer la lecture depuis la base

            List<Utilisateur> users = em.createQuery("SELECT u FROM Utilisateur u", Utilisateur.class).getResultList();
            for (Utilisateur u : users) {
                System.out.println("  • " + u);
                for (Reservation r : u.getReservations()) {
                    System.out.println("    └─ " + r);
                    System.out.println("       └─ " + r.getSalle());
                    for (Equipement eq : r.getSalle().getEquipements()) {
                        System.out.println("          └─ " + eq);
                    }
                }
            }
            System.out.println();

            // -------------------------------------------------------------------------
            // STEP 11.A : Test de suppression orpheline (orphanRemoval = true)
            // -------------------------------------------------------------------------
            System.out.println(">>> [ÉTAPE 3] Test de la suppression orpheline (orphanRemoval=true)...");
            em.getTransaction().begin();

            Utilisateur uAlice = em.find(Utilisateur.class, user.getId());
            if (!uAlice.getReservations().isEmpty()) {
                Reservation resToRemove = uAlice.getReservations().iterator().next();
                Long removedResId = resToRemove.getId();
                System.out.println("  • Suppression de la réservation (ID=" + removedResId + ") de la collection de l'utilisateur...");
                
                // En retirant la réservation de la collection, orphanRemoval=true déclenche un DELETE en base
                uAlice.removeReservation(resToRemove);
                em.getTransaction().commit();

                // Vérification en base
                Reservation checkRes = em.find(Reservation.class, removedResId);
                System.out.println("✔ [VÉRIFICATION] Réservation en base après suppression de la collection: " + 
                                   (checkRes == null ? "NULL (Supprimée automatiquement par orphanRemoval)" : "Présente"));
            }
            System.out.println();

            // -------------------------------------------------------------------------
            // STEP 11.B : Test de suppression d'un utilisateur et cascade REMOVE
            // -------------------------------------------------------------------------
            System.out.println(">>> [ÉTAPE 4] Test de suppression d'un utilisateur avec cascade REMOVE...");
            em.getTransaction().begin();

            // Ajoutons un second utilisateur avec une réservation pour tester la cascade de suppression
            Utilisateur bob = new Utilisateur("Bob Martin", "bob@example.com");
            Salle salleA = em.find(Salle.class, salle.getId());
            Reservation resBob = new Reservation(
                LocalDateTime.now().plusDays(2).withHour(14).withMinute(0),
                LocalDateTime.now().plusDays(2).withHour(16).withMinute(0),
                "Entretien Recrutement Développeur JEE"
            );
            resBob.setSalle(salleA);
            bob.addReservation(resBob);
            em.persist(bob);
            em.getTransaction().commit();

            System.out.println("  • Utilisateur Bob créé avec réservation ID=" + resBob.getId());

            // Maintenant supprimons Bob
            em.getTransaction().begin();
            Utilisateur bobToDel = em.find(Utilisateur.class, bob.getId());
            Long bobResId = resBob.getId();
            Long salleAId = salleA.getId();

            System.out.println("  • Suppression de Bob (ID=" + bobToDel.getId() + ") via em.remove()...");
            em.remove(bobToDel);
            em.getTransaction().commit();

            // Vérifications
            Reservation checkBobRes = em.find(Reservation.class, bobResId);
            Salle checkSalleA = em.find(Salle.class, salleAId);
            List<Equipement> checkEquips = em.createQuery("SELECT e FROM Equipement e", Equipement.class).getResultList();

            System.out.println("✔ [VÉRIFICATION] Réservation de Bob en base: " + 
                               (checkBobRes == null ? "NULL (Supprimée en cascade avec Bob)" : "Présente"));
            System.out.println("✔ [VÉRIFICATION] Salle A en base: " + 
                               (checkSalleA != null ? "TOUJOURS EXISTANTE (ID=" + checkSalleA.getId() + ", non supprimée car entité partagée)" : "NULL"));
            System.out.println("✔ [VÉRIFICATION] Équipements en base: " + checkEquips.size() + " équipements intacts (non supprimés en cascade).");

            System.out.println("\n===============================================================================");
            System.out.println("  TOUTES LES ÉTAPES ET TESTS DU TP 3 ONT ÉTÉ EXÉCUTÉS AVEC SUCCÈS !");
            System.out.println("===============================================================================");

        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            e.printStackTrace();
        } finally {
            em.close();
            emf.close();
        }
    }
}
