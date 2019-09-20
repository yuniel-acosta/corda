package net.corda.mappedschemademo.workflows.test;

import net.corda.mappedschemademo.contracts.schema.InvoiceFinanceDealSchemaV1;
import net.corda.testing.node.StartedMockNode;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.criteria.*;
import java.util.List;

public class FlowTest {

    @Test
    public void TestSomething() {

//        StartedMockNode a;
//
//        a.getServices().withEntityManager((EntityManager entityManager) -> {
//            CriteriaBuilder cb  = entityManager.getCriteriaBuilder();
//            CriteriaQuery<InvoiceFinanceDealSchemaV1.PersistentInvoiceFinanceDeal> cq = entityManager.getCriteriaBuilder().createQuery(InvoiceFinanceDealSchemaV1.PersistentInvoiceFinanceDeal.class);
//            Root<InvoiceFinanceDealSchemaV1.PersistentInvoiceFinanceDeal> root = cq.from(InvoiceFinanceDealSchemaV1.PersistentInvoiceFinanceDeal.class);
//
//            Predicate p = cb.conjunction();
//            ParameterExpression<String> txId = cb.parameter(String.class, "txId");
//            p = cb.and(p, cb.equal(root.get(InvoiceFinanceDealSchemaV1.PersistentInvoiceFinanceDeal.stateRef)))
//
//
//            cq.select(root);
//            List<InvoiceFinanceDealSchemaV1.PersistentInvoiceFinanceDeal> results = entityManager.createQuery(cq).getResultList();
//        });



    }
}
