/*******************************************************************************
  * Copyright (c) 2017 DocDoku.
  * All rights reserved. This program and the accompanying materials
  * are made available under the terms of the Eclipse Public License v1.0
  * which accompanies this distribution, and is available at
  * http://www.eclipse.org/legal/epl-v10.html
  *
  * Contributors:
  *    DocDoku - initial API and implementation
  *******************************************************************************/

package org.polarsys.eplmp.server.dao;

import org.polarsys.eplmp.core.common.Workspace;
import org.polarsys.eplmp.core.meta.*;
import org.polarsys.eplmp.core.product.*;
import org.polarsys.eplmp.core.query.Query;
import org.polarsys.eplmp.core.query.QueryRule;

import javax.persistence.*;
import javax.persistence.criteria.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Morgan Guimard on 09/04/15.
 */
public class PartRevisionQueryDAO {

    private EntityManager em;
    private Locale mLocale;
    private String mTimeZone;

    private CriteriaBuilder cb;
    private CriteriaQuery<PartRevision> cq;

    private Root<PartRevision> pr;
    private Root<PartIteration> pi;
    private Root<Tag> tag;
    private Root<InstanceURLAttribute> iua;
    private Root<InstanceBooleanAttribute> iba;
    private Root<InstanceNumberAttribute> ina;
    private Root<InstanceListOfValuesAttribute> ila;
    private Root<InstanceDateAttribute> ida;
    private Root<InstanceTextAttribute> ita;
    private Root<InstanceLongTextAttribute> ilta;
    private Root<InstancePartNumberAttribute> ipna;

    private static final Logger LOGGER = Logger.getLogger(PartRevisionQueryDAO.class.getName());

    public PartRevisionQueryDAO(Locale pLocale, String pTimeZone,  EntityManager pEM) {
        em = pEM;
        mLocale = pLocale;
        mTimeZone = pTimeZone;
        cb = em.getCriteriaBuilder();

        cq = cb.createQuery(PartRevision.class);
        pr = cq.from(PartRevision.class);
        pi = cq.from(PartIteration.class);
        tag = cq.from(Tag.class);

        iua = cq.from(InstanceURLAttribute.class);
        iba = cq.from(InstanceBooleanAttribute.class);
        ina = cq.from(InstanceNumberAttribute.class);
        ila = cq.from(InstanceListOfValuesAttribute.class);
        ida = cq.from(InstanceDateAttribute.class);
        ilta = cq.from(InstanceLongTextAttribute.class);
        ipna = cq.from(InstancePartNumberAttribute.class);
        ita = cq.from(InstanceTextAttribute.class);
    }

    public List<PartRevision> runQuery(Workspace workspace, Query query) {

        cq.select(pr);

        Predicate prJoinPredicate = cb.and(
                cb.equal(pi.get("partRevision"), pr),
                cb.equal(pr.get("partMaster").get("workspace"), workspace)
        );

        Predicate rulesPredicate = getPredicate(query.getQueryRule());

        cq.where(cb.and(
                prJoinPredicate,
                rulesPredicate
        ));

        TypedQuery<PartRevision> tp = em.createQuery(cq);

        Set<PartRevision> revisions = tp.getResultList().stream()
                .filter(part -> part.getLastCheckedInIteration() != null)
                .collect(Collectors.toSet());

        return new ArrayList<>(revisions);
    }

    private Predicate getPredicate(QueryRule queryRule) {

        if (queryRule == null) {
            return cb.and();
        }

        String condition = queryRule.getCondition();

        List<QueryRule> subQueryRules = queryRule.getSubQueryRules();

        if (subQueryRules != null && !subQueryRules.isEmpty()) {

            Predicate[] predicates = new Predicate[subQueryRules.size()];

            for (int i = 0; i < predicates.length; i++) {
                Predicate predicate = getPredicate(subQueryRules.get(i));
                predicates[i] = predicate;
            }

            if ("OR".equals(condition)) {
                return cb.or(predicates);
            } else if ("AND".equals(condition)) {
                return cb.and(predicates);
            }

            throw new IllegalArgumentException("Cannot parse rule or sub rule condition: " + condition + " ");

        } else {
            return getRulePredicate(queryRule);
        }
    }

    private Predicate getRulePredicate(QueryRule queryRule) {

        String field = queryRule.getField();

        if (field == null) {
            return cb.and();
        }

        String operator = queryRule.getOperator();
        List<String> values = queryRule.getValues();
        String type = queryRule.getType();

        if (field.startsWith("pm.")) {
            return getPartMasterPredicate(field.substring(3), operator, values, type);
        }

        if (field.startsWith("pr.")) {
            return getPartRevisionPredicate(field.substring(3), operator, values, type);
        }

        if (field.startsWith("author.")) {
            return getAuthorPredicate(field.substring(7), operator, values, type);
        }

        if (field.startsWith("attr-TEXT.")) {
            return getInstanceTextAttributePredicate(field.substring(10), operator, values);
        }

        if (field.startsWith("attr-LONG_TEXT.")) {
            return getInstanceLongTextAttributePredicate(field.substring(15), operator, values);
        }

        if (field.startsWith("attr-DATE.")) {
            return getInstanceDateAttributePredicate(field.substring(10), operator, values);
        }

        if (field.startsWith("attr-BOOLEAN.")) {
            return getInstanceBooleanAttributePredicate(field.substring(13), operator, values);
        }

        if (field.startsWith("attr-URL.")) {
            return getInstanceURLAttributePredicate(field.substring(9), operator, values);
        }

        if (field.startsWith("attr-NUMBER.")) {
            return getInstanceNumberAttributePredicate(field.substring(12), operator, values);
        }

        if (field.startsWith("attr-LOV.")) {
            return getInstanceLovAttributePredicate(field.substring(9), operator, values);
        }

        if (field.startsWith("attr-PART_NUMBER.")) {
            return getInstancePartNumberAttributePredicate(field.substring(17), operator, values);
        }

        throw new IllegalArgumentException("Unhandled attribute: [" + field + ", " + operator + ", " + values + "]");
    }

    private Predicate getAuthorPredicate(String field, String operator, List<String> values, String type) {
        return QueryPredicateBuilder.getExpressionPredicate(cb, pr.get("author").get("account").get(field), operator, values, type, mTimeZone);
    }

    private Predicate getPartRevisionPredicate(String field, String operator, List<String> values, String type) {
        if ("checkInDate".equals(field)) {
            Predicate lastIterationPredicate = cb.equal(cb.size(pr.get("partIterations")), pi.get("iteration"));
            return cb.and(lastIterationPredicate, QueryPredicateBuilder.getExpressionPredicate(cb, pi.get("checkInDate"), operator, values, type, mTimeZone));
        }
        else if ("modificationDate".equals(field)) {
            Predicate lastIterationPredicate = cb.equal(cb.size(pr.get("partIterations")), pi.get("iteration"));
            return cb.and(lastIterationPredicate, QueryPredicateBuilder.getExpressionPredicate(cb, pi.get("modificationDate"), operator, values, type, mTimeZone));
        } else if ("status".equals(field)) {
            if (values.size() == 1) {
                return QueryPredicateBuilder.getExpressionPredicate(cb, pr.get(field), operator, values, "status", mTimeZone);
            }
        } else if ("tags".equals(field)) {
            return getTagsPredicate(values);
        } else if ("linkedDocuments".equals(field)) {
            // should be ignored, returning always true for the moment
            return cb.and();
        }
        return QueryPredicateBuilder.getExpressionPredicate(cb, pr.get(field), operator, values, type, mTimeZone);
    }

    private Predicate getTagsPredicate(List<String> values) {
        Predicate prPredicate = tag.in(pr.get("tags"));
        Predicate valuesPredicate = cb.equal(tag.get("label"), values);
        return cb.and(prPredicate, valuesPredicate);
    }

    private Predicate getPartMasterPredicate(String field, String operator, List<String> values, String type) {
        return QueryPredicateBuilder.getExpressionPredicate(cb, pr.get("partMaster").get(field), operator, values, type, mTimeZone);
    }

    // Instances Attributes
    private Predicate getInstanceURLAttributePredicate(String field, String operator, List<String> values) {

        Predicate valuesPredicate = QueryPredicateBuilder.getExpressionPredicate(cb, iua.get("urlValue"), operator, values, "string", mTimeZone);
        Predicate memberPredicate = iua.in(pi.get("instanceAttributes"));
        return cb.and(cb.equal(iua.get("name"), field), valuesPredicate, memberPredicate);
    }

    private Predicate getInstanceBooleanAttributePredicate(String field, String operator, List<String> values) {
        if (values.size() == 1) {
            Predicate valuesPredicate = cb.equal(iba.get("booleanValue"), Boolean.parseBoolean(values.get(0)));
            Predicate memberPredicate = iba.in(pi.get("instanceAttributes"));
            switch (operator) {
                case "equal":
                    return cb.and(cb.equal(iba.get("name"), field), valuesPredicate, memberPredicate);
                case "not_equal":
                    return cb.and(cb.equal(iba.get("name"), field), valuesPredicate.not(), memberPredicate);
                default:
                    break;
            }
        }

        throw new IllegalArgumentException("Cannot handle such operator [" + operator + "] on field " + field + "]");
    }

    private Predicate getInstanceNumberAttributePredicate(String field, String operator, List<String> values) {
        Predicate valuesPredicate = QueryPredicateBuilder.getExpressionPredicate(cb, ina.get("numberValue"), operator, values, "double", mTimeZone);
        Predicate memberPredicate = ina.in(pi.get("instanceAttributes"));
        return cb.and(cb.equal(ina.get("name"), field), valuesPredicate, memberPredicate);
    }

    private Predicate getInstanceLovAttributePredicate(String field, String operator, List<String> values) {
        if (values.size() == 1) {
            Predicate valuesPredicate = cb.equal(ila.get("indexValue"), Integer.parseInt(values.get(0)));
            Predicate memberPredicate = ila.in(pi.get("instanceAttributes"));
            switch (operator) {
                case "equal":
                    return cb.and(cb.equal(ila.get("name"), field), valuesPredicate, memberPredicate);
                case "not_equal":
                    return cb.and(cb.equal(ila.get("name"), field), valuesPredicate.not(), memberPredicate);
                default:
                    break;
            }
        }

        throw new IllegalArgumentException("Cannot handle such operator [" + operator + "] on field " + field + "]");
    }

    private Predicate getInstanceDateAttributePredicate(String field, String operator, List<String> values) {
        Predicate valuesPredicate = QueryPredicateBuilder.getExpressionPredicate(cb, ida.get("dateValue"), operator, values, "date", mTimeZone);
        Predicate memberPredicate = ida.in(pi.get("instanceAttributes"));
        return cb.and(cb.equal(ida.get("name"), field), valuesPredicate, memberPredicate);
    }

    private Predicate getInstanceLongTextAttributePredicate(String field, String operator, List<String> values) {
        Predicate valuesPredicate = QueryPredicateBuilder.getExpressionPredicate(cb, ilta.get("longTextValue"), operator, values, "string", mTimeZone);
        Predicate memberPredicate = ilta.in(pi.get("instanceAttributes"));
        return cb.and(cb.equal(ita.get("name"), field), valuesPredicate, memberPredicate);
    }

    private Predicate getInstancePartNumberAttributePredicate(String field, String operator, List<String> values) {
        Predicate valuesPredicate = QueryPredicateBuilder.getExpressionPredicate(cb, ipna.get("partMasterValue").get("number"), operator, values, "string", mTimeZone);
        Predicate memberPredicate = ipna.in(pi.get("instanceAttributes"));
        return cb.and(cb.equal(ita.get("name"), field), valuesPredicate, memberPredicate);
    }

    private Predicate getInstanceTextAttributePredicate(String field, String operator, List<String> values) {
        Predicate valuesPredicate = QueryPredicateBuilder.getExpressionPredicate(cb, ita.get("textValue"), operator, values, "string", mTimeZone);
        Predicate memberPredicate = ita.in(pi.get("instanceAttributes"));
        return cb.and(cb.equal(ita.get("name"), field), valuesPredicate, memberPredicate);
    }
}


