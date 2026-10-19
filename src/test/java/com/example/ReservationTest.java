package com.example;

import com.example.model.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

public class ReservationTest {

    private EntityManagerFactory emf;
    private EntityManager em;

    @Before
    public void setUp() {
        emf = Persistence.createEntityManagerFactory("gestion-reservations-pu");
        em = emf.createEntityManager();
    }

    @After
    public void tearDown() {
        if (em != null && em.isOpen()) em.close();
        if (emf != null && emf.isOpen()) emf.close();
    }

    @Test
    public void testCascadePersistGraph() {
        em.getTransaction().begin();

        Utilisateur user = new Utilisateur("Alice Dupont", "alice.test@example.com");
        Salle salle = new Salle("Salle B", 40);
        Equipement eq1 = new Equipement("Vidéoprojecteur HD");
        Equipement eq2 = new Equipement("Micro sans fil");

        salle.addEquipement(eq1);
        salle.addEquipement(eq2);

        Reservation res = new Reservation(
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(1).plusHours(2),
            "Conférence Annuelle"
        );
        res.setSalle(salle);
        user.addReservation(res);

        em.persist(user);
        em.getTransaction().commit();

        em.clear();

        Utilisateur fetchedUser = em.find(Utilisateur.class, user.getId());
        assertNotNull(fetchedUser);
        assertEquals(1, fetchedUser.getReservations().size());

        Reservation fetchedRes = fetchedUser.getReservations().iterator().next();
        assertNotNull(fetchedRes.getSalle());
        assertEquals("Salle B", fetchedRes.getSalle().getNom());
        assertEquals(2, fetchedRes.getSalle().getEquipements().size());
    }

    @Test
    public void testOrphanRemoval() {
        em.getTransaction().begin();

        Utilisateur user = new Utilisateur("Charlie Brown", "charlie@example.com");
        Salle salle = new Salle("Salle C", 15);
        Reservation res = new Reservation(LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Réunion 1");
        res.setSalle(salle);
        user.addReservation(res);

        em.persist(user);
        em.getTransaction().commit();

        Long resId = res.getId();
        assertNotNull(resId);

        em.clear();

        // Suppression orpheline
        em.getTransaction().begin();
        Utilisateur fetched = em.find(Utilisateur.class, user.getId());
        Reservation toRemove = fetched.getReservations().iterator().next();
        fetched.getReservations().remove(toRemove);
        em.getTransaction().commit();

        em.clear();
        assertNull("La réservation orpheline doit être supprimée de la base", em.find(Reservation.class, resId));
    }

    @Test
    public void testCascadeRemoveUserPreservesSalleAndEquipement() {
        em.getTransaction().begin();

        Utilisateur user = new Utilisateur("David Miller", "david@example.com");
        Salle salle = new Salle("Salle D", 50);
        Equipement eq = new Equipement("Caméra Visioconférence");
        salle.addEquipement(eq);

        Reservation res = new Reservation(LocalDateTime.now(), LocalDateTime.now().plusHours(3), "Formation");
        res.setSalle(salle);
        user.addReservation(res);

        em.persist(user);
        em.getTransaction().commit();

        Long userId = user.getId();
        Long resId = res.getId();
        Long salleId = salle.getId();
        Long eqId = eq.getId();

        // Suppression de l'utilisateur
        em.getTransaction().begin();
        Utilisateur userToDel = em.find(Utilisateur.class, userId);
        em.remove(userToDel);
        em.getTransaction().commit();

        em.clear();
        assertNull("L'utilisateur doit être supprimé", em.find(Utilisateur.class, userId));
        assertNull("La réservation doit être supprimée en cascade", em.find(Reservation.class, resId));
        assertNotNull("La salle ne doit PAS être supprimée", em.find(Salle.class, salleId));
        assertNotNull("L'équipement ne doit PAS être supprimé", em.find(Equipement.class, eqId));
    }
}
