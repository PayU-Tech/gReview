/**
 * Copyright 2012 Houghton Associates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.houghtonassociates.bamboo.plugins.view;

import com.atlassian.bamboo.build.ChainResultsAction;
import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.resultsummary.vcs.RepositoryChangeset;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.bamboo.ww2.aware.permissions.PlanReadSecurityAware;
import com.google.common.collect.Lists;
import com.houghtonassociates.bamboo.plugins.GerritRepositoryAdapter;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO;
import com.houghtonassociates.bamboo.plugins.dao.GerritService;
import org.apache.log4j.Logger;

/**
 * @author Jason Huntley
 *
 */
public class ViewGerritChainResultsAction extends ChainResultsAction implements PlanReadSecurityAware {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ViewGerritChainResultsAction.class);
    private static final String GERRIT_REPOSITORY_PLUGIN_KEY = "com.houghtonassociates.bamboo.plugins.gReview:gerrit";

    private GerritChangeVO changeVO;
    private GerritService gerritService;

    public ViewGerritChainResultsAction() {
        changeVO = new GerritChangeVO();
    }


    @Override
    public String execute() throws Exception {
        updateChangeVO();

        if (getImmutableChain() == null || getImmutableChain().isMarkedForDeletion()) {
            addActionError(getText("chain.error.noChain", Lists.newArrayList(getPlanKey())));
            return ERROR;
        } else if (getChainResult() == null) {
            if (getChainResultNumber() > 0) {
                PlanResultKey planResultKey = PlanKeys.getPlanResultKey(getImmutableChain().getPlanKey(), getChainResultNumber());
                ChainResultsSummary chainResult = resultsSummaryManager.getResultsSummary(planResultKey, ChainResultsSummary.class);
                if (chainResult == null) {
                    addActionError(getText("chain.error.noChainResult", Lists.newArrayList(getPlanKey() + "-" + getChainResultNumber())));
                    return ERROR;
                } else {
                    setChainResult(chainResult);
                }
            } else {
                addActionError(getText("chain.error.noChainResult", Lists.newArrayList(getPlanKey() + "-" + getChainResultNumber())));
                return ERROR;
            }
        }

        return SUCCESS;
    }

    public GerritChangeVO getChange() {
        return changeVO;
    }

    public String getRevision() {
        String result = null;

        for (RepositoryChangeset changeset : getResultsSummary().getRepositoryChangesets()) {
            if (changeset.getRepositoryData().getPluginKey().equals(GERRIT_REPOSITORY_PLUGIN_KEY)) {
                result = changeset.getChangesetId();
                break;
            }
        }

        return result;
    }

    private void updateChangeVO() throws RepositoryException {
        final String revision = this.getRevision();

        if (revision == null) {
            changeVO = new GerritChangeVO();
        } else {
            final GerritChangeVO change = getGerritService().getChangeByRevision(revision);

            if (change == null) {
                LOG.error(this.getTextProvider().getText("repository.gerrit.messages.error.retrieve"));
                changeVO = new GerritChangeVO();
            } else {
                changeVO = change;
            }
        }
    }

    private static RepositoryDefinition getDefaultRepository(ImmutablePlan plan) {
        ImmutableJob job = (ImmutableJob)Narrow.to(plan, ImmutableJob.class);
        if (job != null) {
            return job.getParent().getEffectiveRepositoryDefinitions().get(0);
        }

        ImmutableChain chain = (ImmutableChain) Narrow.to(plan, ImmutableChain.class);
        if (chain != null) {
            return chain.getEffectiveRepositoryDefinitions().get(0);
        }

        throw new IllegalArgumentException("Don't know how to get repository definitions for " + plan.getClass());
    }

    private GerritRepositoryAdapter getRepository() {
        GerritRepositoryAdapter repository = null;

        Repository repo = getDefaultRepository(getImmutablePlan()).getRepository();

        if (repo instanceof GerritRepositoryAdapter) {
            repository = (GerritRepositoryAdapter) repo;
        }

        return repository;
    }

    private GerritService getGerritService() throws RepositoryException {
        if (gerritService == null) {
            Repository repo = getDefaultRepository(getImmutablePlan()).getRepository();

            if (repo instanceof GerritRepositoryAdapter) {
                GerritRepositoryAdapter gra = getRepository();
                gerritService = gra.getGerritDAO();
            }
        }

        return gerritService;
    }
}