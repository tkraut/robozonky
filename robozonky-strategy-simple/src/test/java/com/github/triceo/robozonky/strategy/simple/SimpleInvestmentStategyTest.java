/*
 * Copyright 2016 Lukáš Petrovický
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.triceo.robozonky.strategy.simple;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.github.triceo.robozonky.PortfolioOverview;
import com.github.triceo.robozonky.remote.Loan;
import com.github.triceo.robozonky.remote.Rating;
import com.github.triceo.robozonky.strategy.InvestmentStrategy;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

public class SimpleInvestmentStategyTest {

    private static final Rating RATING_A = Rating.A;
    private static final Rating RATING_B = Rating.B;
    private static final int TESTED_TERM_LENGTH = 2;
    private static final int MAXIMUM_LOAN_INVESTMENT = 10000;
    private static final int MINIMUM_ASK = SimpleInvestmentStategyTest.MAXIMUM_LOAN_INVESTMENT / 10;
    private static final int MAXIMUM_ASK = SimpleInvestmentStategyTest.MAXIMUM_LOAN_INVESTMENT * 10;
    private static final BigDecimal MAXIMUM_LOAN_SHARE_A = BigDecimal.valueOf(0.01);
    private static final BigDecimal TARGET_SHARE_A = BigDecimal.valueOf(0.2);
    private static final StrategyPerRating STRATEGY_A = new StrategyPerRating(SimpleInvestmentStategyTest.RATING_A,
            SimpleInvestmentStategyTest.TARGET_SHARE_A, SimpleInvestmentStategyTest.TESTED_TERM_LENGTH - 1, -1, 200,
            SimpleInvestmentStategyTest.MAXIMUM_LOAN_INVESTMENT, BigDecimal.ZERO,
            SimpleInvestmentStategyTest.MAXIMUM_LOAN_SHARE_A, SimpleInvestmentStategyTest.MINIMUM_ASK,
            SimpleInvestmentStategyTest.MAXIMUM_ASK, true
    );

    private static final BigDecimal MAXIMUM_LOAN_SHARE_B = BigDecimal.valueOf(0.001);
    private static final BigDecimal TARGET_SHARE_B = SimpleInvestmentStategyTest.TARGET_SHARE_A.add(BigDecimal.valueOf(0.5));
    private static final StrategyPerRating STRATEGY_B = new StrategyPerRating(SimpleInvestmentStategyTest.RATING_B,
            SimpleInvestmentStategyTest.TARGET_SHARE_B, SimpleInvestmentStategyTest.TESTED_TERM_LENGTH - 1,
            SimpleInvestmentStategyTest.TESTED_TERM_LENGTH + 1, 200,
            SimpleInvestmentStategyTest.MAXIMUM_LOAN_INVESTMENT, BigDecimal.ZERO,
            SimpleInvestmentStategyTest.MAXIMUM_LOAN_SHARE_B, SimpleInvestmentStategyTest.MINIMUM_ASK,
            SimpleInvestmentStategyTest.MAXIMUM_ASK, false
    );

    private final InvestmentStrategy overallStrategy;

    public SimpleInvestmentStategyTest() {
        final Map<Rating, StrategyPerRating> strategies = new EnumMap<>(Rating.class);
        strategies.put(Rating.AAAAA, Mockito.mock(StrategyPerRating.class));
        strategies.put(Rating.AAAA, Mockito.mock(StrategyPerRating.class));
        strategies.put(Rating.AAA, Mockito.mock(StrategyPerRating.class));
        strategies.put(Rating.AA, Mockito.mock(StrategyPerRating.class));
        strategies.put(SimpleInvestmentStategyTest.RATING_A, SimpleInvestmentStategyTest.STRATEGY_A);
        strategies.put(SimpleInvestmentStategyTest.RATING_B, SimpleInvestmentStategyTest.STRATEGY_B);
        strategies.put(Rating.C, Mockito.mock(StrategyPerRating.class));
        strategies.put(Rating.D, Mockito.mock(StrategyPerRating.class));
        overallStrategy = new SimpleInvestmentStrategy(0, Integer.MAX_VALUE, strategies);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorEnsuringAllStrategiesPresent() {
        final Map<Rating, StrategyPerRating> strategies = new EnumMap<>(Rating.class);
        strategies.put(SimpleInvestmentStategyTest.RATING_A, SimpleInvestmentStategyTest.STRATEGY_A);
        strategies.put(SimpleInvestmentStategyTest.RATING_B, SimpleInvestmentStategyTest.STRATEGY_B);
        new SimpleInvestmentStrategy(0, Integer.MAX_VALUE, strategies);
    }

    @Test
    public void properlyRecommendingInvestmentSize() {
        final BigDecimal loanAmount = BigDecimal.valueOf(100000.0);
        final Loan mockLoan = Mockito.mock(Loan.class);
        Mockito.when(mockLoan.getRating()).thenReturn(SimpleInvestmentStategyTest.RATING_A);
        Mockito.when(mockLoan.getRemainingInvestment()).thenReturn(loanAmount.doubleValue());
        Mockito.when(mockLoan.getAmount()).thenReturn(loanAmount.doubleValue());

        // with unlimited balance
        final PortfolioOverview portfolio = Mockito.mock(PortfolioOverview.class);
        Mockito.when(portfolio.getCzkAvailable()).thenReturn(Integer.MAX_VALUE);
        Mockito.when(portfolio.getCzkInvested()).thenReturn(0);
        final int maxInvestment = Math.min(
                loanAmount.multiply(SimpleInvestmentStategyTest.MAXIMUM_LOAN_SHARE_A).intValue(),
                SimpleInvestmentStategyTest.MAXIMUM_LOAN_INVESTMENT);
        final int actualInvestment = overallStrategy.recommendInvestmentAmount(mockLoan, portfolio);
        Assertions.assertThat(actualInvestment).isLessThanOrEqualTo(maxInvestment);

        // with balance just a little less than the recommended investment
        Mockito.when(portfolio.getCzkAvailable()).thenReturn(actualInvestment - 1);
        final int adjustedForBalance = overallStrategy.recommendInvestmentAmount(mockLoan, portfolio);
        Assertions.assertThat(adjustedForBalance).isLessThanOrEqualTo(actualInvestment);

        // and make sure the recommendation is 200 times X, where X is a positive integer
        final BigDecimal remainder = BigDecimal.valueOf(adjustedForBalance)
                .remainder(BigDecimal.valueOf(InvestmentStrategy.MINIMAL_INVESTMENT_INCREMENT));
        Assertions.assertThat(remainder.intValue()).isEqualTo(0);
    }

    private static Map<Rating, BigDecimal> prepareShareMap(final BigDecimal ratingA, final BigDecimal ratingB,
                                                           final BigDecimal ratingC) {
        final Map<Rating, BigDecimal> map = new EnumMap<>(Rating.class);
        map.put(Rating.A, ratingA);
        map.put(Rating.B, ratingB);
        map.put(Rating.C, ratingC);
        return Collections.unmodifiableMap(map);
    }

    private static void assertOrder(final List<Rating> result, final Rating... ratingsOrderedDown) {
        Assertions.assertThat(result).hasSize(ratingsOrderedDown.length);
        if (ratingsOrderedDown.length < 2) {
            return;
        } else if (ratingsOrderedDown.length > 3) {
            throw new IllegalStateException("This should never happen in the test.");
        }
        final Rating first = result.get(0);
        final Rating last = result.get(result.size() - 1);
        Assertions.assertThat(first).isGreaterThan(last);
        Assertions.assertThat(first).isEqualTo(ratingsOrderedDown[0]);
        Assertions.assertThat(last).isEqualTo(ratingsOrderedDown[ratingsOrderedDown.length - 1]);
    }

    private static Loan mockLoan(final int id, final double amount, final int term, final Rating rating) {
        final Loan loan = Mockito.mock(Loan.class);
        Mockito.when(loan.getRemainingInvestment()).thenReturn(amount * 2);
        Mockito.when(loan.getAmount()).thenReturn(amount);
        Mockito.when(loan.getId()).thenReturn(id);
        Mockito.when(loan.getTermInMonths()).thenReturn(term);
        Mockito.when(loan.getRating()).thenReturn(rating);
        return loan;
    }

    @Test
    public void testProperSorting() {
        final Loan a1 = SimpleInvestmentStategyTest.mockLoan(1, 2, 3, Rating.A);
        final Loan b1 = SimpleInvestmentStategyTest.mockLoan(4, 5, 6, Rating.B);
        final Loan c1 = SimpleInvestmentStategyTest.mockLoan(7, 8, 9, Rating.C);
        final Loan a2 = SimpleInvestmentStategyTest.mockLoan(10, 11, 12, Rating.A);
        final Collection<Loan> loans = Arrays.asList(a1, b1, c1, a2, b1, c1);
        final Map<Rating, Collection<Loan>> result = SimpleInvestmentStrategy.sortLoansByRating(loans);
        Assertions.assertThat(result).containsOnlyKeys(Rating.A, Rating.B, Rating.C);
        Assertions.assertThat(result.get(Rating.A)).containsExactly(a1, a2);
        Assertions.assertThat(result.get(Rating.B)).containsExactly(b1);
        Assertions.assertThat(result.get(Rating.C)).containsExactly(c1);
    }

    @Test
    public void properRankingOfRatings() {
        final BigDecimal targetShareA = BigDecimal.valueOf(0.001);
        final BigDecimal targetShareB = targetShareA.multiply(BigDecimal.TEN);
        final BigDecimal targetShareC = targetShareB.multiply(BigDecimal.TEN);

        final Map<Rating, StrategyPerRating> strategies = Arrays.stream(Rating.values())
                .collect(Collectors.toMap(Function.identity(), r -> Mockito.mock(StrategyPerRating.class)));
        strategies.put(Rating.A, new StrategyPerRating(Rating.A, targetShareA, 1, 1, 1, 1, BigDecimal.ZERO,
                BigDecimal.ONE, 1, 1, true));
        strategies.put(Rating.B, new StrategyPerRating(Rating.B, targetShareB, 1, 1, 1, 1, BigDecimal.ZERO,
                BigDecimal.ONE, 1, 1, true));
        strategies.put(Rating.C, new StrategyPerRating(Rating.C, targetShareC, 1, 1, 1, 1, BigDecimal.ZERO,
                BigDecimal.ONE, 1, 1, true));
        final SimpleInvestmentStrategy sis = new SimpleInvestmentStrategy(0, Integer.MAX_VALUE, strategies);

        // all ratings have zero share; C > B > A
        Map<Rating, BigDecimal> tmp = SimpleInvestmentStategyTest.prepareShareMap(BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO);
        SimpleInvestmentStategyTest.assertOrder(sis.rankRatingsByDemand(tmp), Rating.C, Rating.B, Rating.A);

        // A only; B, C overinvested
        tmp = SimpleInvestmentStategyTest.prepareShareMap(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.TEN);
        SimpleInvestmentStategyTest.assertOrder(sis.rankRatingsByDemand(tmp), Rating.A);

        // B > C > A
        tmp = SimpleInvestmentStategyTest.prepareShareMap(BigDecimal.valueOf(0.00095), BigDecimal.ZERO,
                BigDecimal.valueOf(0.099));
        SimpleInvestmentStategyTest.assertOrder(sis.rankRatingsByDemand(tmp), Rating.B, Rating.C, Rating.A);
    }

    private static Map<Rating, StrategyPerRating> mockStrategies() {
        return Arrays.stream(Rating.values())
                .collect(Collectors.toMap(Function.identity(), r -> {
                    final StrategyPerRating s = Mockito.mock(StrategyPerRating.class);
                    Mockito.when(s.isPreferLongerTerms()).thenReturn(r.ordinal() % 2 == 0); // exercise both branches
                    Mockito.when(s.getRating()).thenReturn(r);
                    Mockito.when(s.getTargetShare()).thenReturn(BigDecimal.valueOf(0.1));
                    Mockito.when(s.isAcceptable(Matchers.any())).thenReturn(true);
                    return s;
                }));
    }

    @Test
    public void doesNotAllowUnderinvesting() {
        final int balance = 100;
        final SimpleInvestmentStrategy sis =
                new SimpleInvestmentStrategy(balance, 100000, SimpleInvestmentStategyTest.mockStrategies());

        // all ratings have zero share; C > B > A
        final Map<Rating, BigDecimal> tmp =
                SimpleInvestmentStategyTest.prepareShareMap(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        // prepare some loans
        final Loan a1 = SimpleInvestmentStategyTest.mockLoan(1, 2, 3, Rating.A);
        final Loan b1 = SimpleInvestmentStategyTest.mockLoan(4, 5, 6, Rating.B);
        final Loan c1 = SimpleInvestmentStategyTest.mockLoan(7, 8, 9, Rating.C);
        final Loan a2 = SimpleInvestmentStategyTest.mockLoan(10, 11, 12, Rating.A);
        final List<Loan> loans = Arrays.asList(a1, b1, c1, a2);

        // make sure we never go below the minimum balance
        final PortfolioOverview portfolio = Mockito.mock(PortfolioOverview.class);
        Mockito.when(portfolio.getSharesOnInvestment()).thenReturn(tmp);
        Mockito.when(portfolio.getCzkInvested()).thenReturn(0);
        Mockito.when(portfolio.getCzkAvailable()).thenReturn(balance + 1);
        Assertions.assertThat(sis.getMatchingLoans(loans, portfolio)).isNotEmpty();
        Mockito.when(portfolio.getCzkAvailable()).thenReturn(balance);
        Assertions.assertThat(sis.getMatchingLoans(loans, portfolio)).isNotEmpty();
        Mockito.when(portfolio.getCzkAvailable()).thenReturn(balance - 1);
        Assertions.assertThat(sis.getMatchingLoans(loans, portfolio)).isEmpty();
    }

    @Test
    public void doesNotAllowInvestingOverCeiling() {
        final int balance = 10000;
        final int ceiling = 100000;
        final SimpleInvestmentStrategy sis =
                new SimpleInvestmentStrategy(balance, ceiling, SimpleInvestmentStategyTest.mockStrategies());

        // all ratings have zero share; C > B > A
        final Map<Rating, BigDecimal> tmp =
                SimpleInvestmentStategyTest.prepareShareMap(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        // prepare some loans
        final Loan a1 = SimpleInvestmentStategyTest.mockLoan(1, 2, 3, Rating.A);
        final Loan b1 = SimpleInvestmentStategyTest.mockLoan(4, 5, 6, Rating.B);
        final Loan c1 = SimpleInvestmentStategyTest.mockLoan(7, 8, 9, Rating.C);
        final Loan a2 = SimpleInvestmentStategyTest.mockLoan(10, 11, 12, Rating.A);
        final List<Loan> loans = Arrays.asList(a1, b1, c1, a2);

        // make sure we never go below the minimum balance
        final PortfolioOverview portfolio = Mockito.mock(PortfolioOverview.class);
        Mockito.when(portfolio.getSharesOnInvestment()).thenReturn(tmp);
        Mockito.when(portfolio.getCzkAvailable()).thenReturn(Integer.MAX_VALUE);
        Mockito.when(portfolio.getCzkInvested()).thenReturn(ceiling + 1);
        Assertions.assertThat(sis.getMatchingLoans(loans, portfolio)).isEmpty();
        Mockito.when(portfolio.getCzkInvested()).thenReturn(ceiling);
        Assertions.assertThat(sis.getMatchingLoans(loans, portfolio)).isNotEmpty();
        Mockito.when(portfolio.getCzkInvested()).thenReturn(ceiling - 1);
        Assertions.assertThat(sis.getMatchingLoans(loans, portfolio)).isNotEmpty();
    }


    @Test
    public void properlySortsMatchesOnTerms() {
        final int balance = 10000;
        final int ceiling = 100000;
        final SimpleInvestmentStrategy sis =
                new SimpleInvestmentStrategy(balance, ceiling, SimpleInvestmentStategyTest.mockStrategies());

        // all ratings have zero share; C > B > A
        final Map<Rating, BigDecimal> tmp =
                SimpleInvestmentStategyTest.prepareShareMap(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        // prepare some loans
        final int aTerm = 3, bTerm = 4;
        final Loan a1 = SimpleInvestmentStategyTest.mockLoan(1, 200, aTerm, Rating.A);
        final Loan b1 = SimpleInvestmentStategyTest.mockLoan(4, 500, bTerm, Rating.B);
        final Loan b2 = SimpleInvestmentStategyTest.mockLoan(7, 800, bTerm + 1, Rating.B);
        final Loan a2 = SimpleInvestmentStategyTest.mockLoan(10, 1100, aTerm + 1, Rating.A);
        final List<Loan> loans = Arrays.asList(a1, b1, a2, b2);

        // prepare portfolio
        final PortfolioOverview portfolio = Mockito.mock(PortfolioOverview.class);
        Mockito.when(portfolio.getSharesOnInvestment()).thenReturn(tmp);
        Mockito.when(portfolio.getCzkAvailable()).thenReturn(Integer.MAX_VALUE);
        Mockito.when(portfolio.getCzkInvested()).thenReturn(0);

        // make sure the loans are properly sorted according to their terms
        final List<Loan> result = sis.getMatchingLoans(loans, portfolio);
        Assertions.assertThat(result).isNotEmpty();
        Assertions.assertThat(result).containsExactly(a2, a1, b1, b2);
    }
}
