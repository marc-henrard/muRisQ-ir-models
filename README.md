# muRisQ-ir-models by Marc Henrard

muRisQ Advisory open source code for Interest Rate Models.

muRisQ stands for Management of Risk by Quantitative methods. The term risk management has to be understood in a large sense which includes risk strategies, ALM, quantitative impacts of regulation, and trading strategies.

You can find more details about our consulting and advisory services on our website [muRisQ Advisory](http://murisq.com/) - Email: [info@murisq.com](mailto:info@murisq.com)

muRisQ Advisory is managed by Marc Henrard. You can find more about his contribution to quantitative finance through his papers and his blog:
* Papers in finance: [Marc Henrard - SSRN](http://ssrn.com/author=352726)
* Blog: [Multi-curve framework](http://multi-curve-framework.blogspot.com)
* Profile: [Marc Henrard - LinkedIn](https://www.linkedin.com/in/marchenrard/)

---

# Repository content

This repository proposes code for pricing and risk management of interest rate derivatives.

The models implemented are based on proprietary research and academic literature as described in each implementation.

Comments and suggestions for improvements are welcome.

## Foundations

Some code proposed in this repository is based on OpenGamma Strata (version 2.6.0) library: http://strata.opengamma.io/

## Products

### 1. Compounded overnight futures
Description of the futures.

### 2. OIS futures
Description of an innovative futures design. 
#### References
* [Henrard, M.](http://multi-curve-framework.blogspot.com) (2018). Risk-Based Overnight-Linked Futures Design. Market infrastructure analysis, muRisQ Advisory, August 2018. Available at SSRN: (https://ssrn.com/abstract=3238640)

### 3. LIBOR Fallback analysis
LIBOR fallback options analysis. Value transfer, convexity adjustments and risk management. Compounding setting in arrears fixing computation.
#### References
* [Henrard, M.](http://multi-curve-framework.blogspot.com) (2019). A Quant Perspective on IBOR Fallback Consultation Results. Market infrastructure analysis, muRisQ Advisory, January 2019.
Available at (https://ssrn.com/abstract=3308766).

### 4. CMS spread periods
Description of CMS spread fields and payoff. CMS spread coupons, caplets and floorlets. Generalized payoff: w1 * rate1 - w2 * rate2

## Curves

### 1. Overnight-Overnight conventions and nodes
Swap conventions to deal with EFFR-SOFR and EONIA-ESTER transition. Associated template and nodes to calibrate curves.

### 2. Multiply curve
Curve description as the multiplication of two underlying curves. Used for intra-month seasonal adjustment in overnight-curves.

## Models

### 1. Bachelier Formula

#### Description
Explicit formula for implicit volatility.

#### References
* Le Floc'h, F. (2016). Fast and Accurate Analytic Basis Point Volatility. Working paper.

### 2. Hull-White one-factor
* Convexity adjustment for futures. Implementation with flexible start and end date.
* Pricing of compounded overnight futures. Reference: Henrard, Marc. (2018) Overnight based futures: convexity adjustment estimation. Available at (https://ssrn.com/abstract=3134346).
* Different formulas for cross-terms: same model parameters (Asian options) or different model parameters (discounting transition)
* Pricing of CMS coupons/caplets/floorlets by efficient approximation.

### 3. G2++

#### Code 
* Parameters description
* Different formulas for variance and volatility in different setup
* Pricing of European swaptions (physical settlement) with efficient explicit approximation and with numerical integration 2D.

### 4. Swaption with 2 collateral

Pricing of swaptions when two collateral rates are used: one for the swaption itself up to a given date before expiry and one other from that date on, including for the delivered swap.

### 5. LIBOR/Forward Market Model

#### Description
LIBOR or Forward Market Model (also called BGM - Brace/Gatarek/Musiela) with displaced diffusion and deterministic LIBOR/OIS spread. The model dynamic is on the forward associated to pseudo-discount factors in a multi-curve and collateral discounting approach. Multi-factor model.

#### References
* [Henrard, M.](http://multi-curve-framework.blogspot.com) (2010). Swaptions in Libor Market Model with local volatility. *Wilmott Journal*, 2(3):135--154.
* [Henrard, M.](http://multi-curve-framework.blogspot.com) (2014). *Interest Rate Modelling in the Multi-curve Framework: Foundations, Evolution and Implementation*. Applied Quantitative Finance. Palgrave Macmillan. ISBN: 978-1-137-37465-3.

#### Code 
* Parameters description
* European physical delivery swaptions price by efficient approximation (preditor-corrector type)
* Monte Carlo rate evolution (Predictor-corrector style)
* Monte Carlo pricers:
  * European swaption
  * IBOR Ratchet
  * CMS coupon, caplet, floorlet
  * Generalized CMS spread coupon, caplet, floorlet

### 5. Rational Multi-curve Model

#### Description
The model describes the evolution of the discount curve and the Libor process in an explicit way. The quantities are not presented through a SDE but directly as explicit quantities depending on simple random variables. This leads to explicit dynamic and in general (almost) explicit formulas for simple instruments and very easy Monte Carlo simulations.

#### References
* [Crepey, S.](https://math.maths.univ-evry.fr/crepey/), [Macrina, A.](http://amacrina.wixsite.com/macrina), Nguyen, T. M., and Skovmand, D. (2016). Rational multi-curve models with counterparty-risk valuation adjustments. *Quantitative Finance* , 16(6): 847-866.
* [Macrina, A.](http://amacrina.wixsite.com/macrina) (2014). Heat kernel models for asset pricing. *International Journal of Theoretical and Applied Finance*, 17(7).
* [Henrard, M.](http://multi-curve-framework.blogspot.com) (2017). Rational multi-curve interest rate model: pricing of liquid market instruments. Working paper.

#### Code 
* Vanilla swaptions with several versions of the model and several numerical approaches (explicit formulas, numerical integration, Monte Carlo). Present value.
* Cap/floor with several versions of the model and several numerical approaches. Present value and implied volatility.

---

# Professional services

The models proposed here are only a small part of the code we have developed or have access to for research and advisory services purposes. Don’t hesitate to contact us if you are interested by other models, require advisory services or are looking for a training around similar models.

## Trainings and workshops

We propose in-house training and workshops on subjects related to quantitative finance and risk management.

We offer extensive flexibility on the training organization.

A in-house tailor-made course with our experts presented to your full team often costs less than sending two people to a standard course organized by a large training firm.

*Agenda tailored to your needs. Detailed lecture notes.*
*Associated to open source code for practical implementation.*
*Training in English or in French*

Some of the popular courses are (course description and typical agendas available through the links):
* Multi-curve and collateral framework: foundations, evolution and implementation. <https://murisq.blogspot.com/p/training.html#multicurve>
* Benchmarks in transition: Quantitative perspective on benchmarks, transition, fallback and regulation. <https://murisq.blogspot.com/p/training.html#libor-future>
* Algorithmic Differentiation in Finance. <https://murisq.blogspot.com/p/training.html#ad>
* Central clearing and bilateral margin. <https://murisq.blogspot.com/p/training.html#margin>

Some recent public courses:
* Workshop Multi-curve and collateral framework. One day workshop at The 10th Fixed Income Conference (Barcelona, Spain), September 2014.
* Collateral, regulation and multi-curve. Belfius Financial Engineering Fund Workshop at KUL/Leuven University (Leuven, Belgium), December 2017.
* Workshop *Interest Rate Modelling in the Multi-curve Framework: Collateral and Regulatory Requirements*. LFS Workshop (London, UK), September 2018.
* Workshop *The future of LIBOR: Quantitative perspective on benchmarks, overnight, fallback and regulation*. Finans Foreningen workshop (Copenhagen, Denmark), 24 January 2019.
* Workshop *Interest Rate Modelling in the Multi-curve Framework: Collateral and Regulatory Requirements*. LFS Workshop (New York, USA), 25-26 March 2019.
* Workshop *Interest Rate Modelling in the Multi-curve Framework: Collateral and Regulatory Requirements*. LFS Workshop (Singapore), 3-4 April 2019.
* Workshop *Interest Rate Modelling in the Multi-curve Framework: Collateral and Regulatory Requirements*. LFS Workshop (London, UK), 23-24 September 2019.
* Workshop *The future of LIBOR: Quantitative perspective on benchmarks, transition, fallback and regulation*. The 15th Quantitative Finance Conference - WBS (Italy, Rome), 16 October 2019.
* Workshop *Interest Rate Modelling in the Multi-curve Framework: Collateral and Regulatory Requirements*. LFS Workshop (Singapore), 4-5 November 2019.
* Workshop *The future of LIBOR: Quantitative perspective on benchmarks, transition, fallback and regulation*. RiskMinds (Amsterdam, The Netherlands), 6 December 2019.
* Workshop *The future of LIBOR: Quantitative perspective on benchmarks, transition, fallback and regulation*. Interest Rate Reform Conference (A Quant Perspective) - WBS (London, UK), 4 March 2020.
* Course *Interest Rate Modelling in the Multi-curve Framework: Collateral and Regulatory Requirements*. In-house course, International Financial Organisation (on-line), April 2020.
* Course *Interest Rate Modelling in the Multi-curve Framework: Collateral and Regulatory Requirements*. LFS course (London, UK), May 2020.
* *Martingales and Fixed Income Valuation*, CQF Module 6. CQF Institute (London, UK), May 2020.
* Multiple in-house courses for commercial banks, central banks, hedge funds, international financial organisations, etc.


## Advisory

* Developments
    * *Multi-curve and collateral framework*. Collateral discounting, impact of CSA, multi-curve calibration, new benchmarks, cheapest-to-deliver
    * *Interest rate models*: Term structure models, smile, negative rates, stochastic spreads.
    * *Exchange traded instruments*: Development of exchanged traded instruments, detailed term sheet, regulatory approval, CCP's risk management procedures.
    * *Margin methodologies*: Variation and Initial Margin methodologies design. Review and implementation of methodologies used by CCPs (replication). Bilateral margin methodologies.
    * *Simulation*: Model implementation for efficient simulation, xVA underlying models 
    * *Benchmarks*: valuation of instruments indexed on new benchmarks, benchmarks transition and discontinuation, LIBOR fallback analysis and solutions, overnight benchmarks (RFR) transition, CCP discounting switch, valuation impacts, risk management, ALM, cost of protocol, discounting big bang
    * *Code*: Large quant libraries available to price and risk manage interest rate books
* Risk management
	* Hedging strategies (design and back testing)
	* Value-at-Risk
	* Variation Margin efficient implementation
	* Initial margin models 
* Model validation
	* Flow instruments: Multi-curve framework, collateral impact, CSA review.
	* Term structure: Multi-factors models; stochastic spreads.
	* VaR: Parametric, historical, Monte Carlo.
	* Smile: Swaption, cap/floor, negative rates, extrapolation.
	* White paper: Independent assessment of new products and services.
* Regulatory impacts
    * *Assessments*: Impact assessments for derivative users.
    * *Bilateral margins*: Quantitative impacts of uncleared margin regulation (UMR), bilateral margin methodologies, ISDA and regulatory SIMM computations.
    * *Compression*: Exposure reduction, portfolio compression
    * *Business strategy*: cleared v uncleared OTC derivatives, cost of trading, access to market infrastructure
    * *Regulatory consultative documents*: Comments on consultative documents.
    * *Negotiation*: Negotiations for efficient access to markets
    
![alt text](https://1.bp.blogspot.com/-7ssBHcvQwXM/W9cqfO3f2UI/AAAAAAAAAGQ/nC0ajGEDfE4txCZdW2Rifx_91XfSZJvtACK4BGAYYCw/s1600/murisq-bookmark-1175.png "muRisQ Advisory banner")

