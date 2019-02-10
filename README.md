# muRisQ Advisory: Interest rate models developments

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

Comments and suggestions for improvements are welcomed.

## Foundations

The pricers proposed in this repository are based on OpenGamma Strata (version 2.2.0) library:
http://strata.opengamma.io/

## Products

### 1. Compounded overnight futures
Description of the futures.
### 2. OIS futures
Description of an innovative futures design. 
#### References
* [Henrard, M.](http://multi-curve-framework.blogspot.com) (2018). Risk-Based Overnight-Linked Futures Design. Market infrastructure analysis, muRisQ Advisory, August 2018. Available at SSRN: (https://ssrn.com/abstract=3238640)

### 2. LIBOR Fallback analysis
LIBOR fallback options analysis. Value transfer, convexity adjustments and risk management. Compounding setting in arrears fixing computation.

#### References
* [Henrard, M.](http://multi-curve-framework.blogspot.com) (2019). A Quant Perspective on IBOR Fallback Consultation Results. Market infrastructure analysis, muRisQ Advisory, January 2019.
Available at (https://ssrn.com/abstract=3308766).

## Models

### 1. Bachelier Formula

#### Description
Explicit formula for implicit volatility.

#### References
* Le Floc'h, F. (2016). Fast and Accurate Analytic Basis Point Volatility. Working paper.

### 2. Hull-White one-factor
* Convexity adjustment for futures. Implementation with flexible start and end date.
* Pricing of compounded overnight futures. Reference: Henrard, Marc. (2018) Overnight based futures: convexity adjustment estimation. Available at (https://ssrn.com/abstract=3134346).

### 3. Rational Multi-curve Model

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

A in-house tailor-made course with our experts presented to your full team often cost less than sending two people to a standard course organized by a large training firm.

*Agenda tailored to your needs. Detailed lecture notes.*
*Associated to open source code for practical implementation.*
*Training in English or French*

Some of the popular courses are (course description and typical agendas available through the links):
* Multi-curve and collateral framework: foundations, evolution and implementation. <https://murisq.blogspot.com/p/training.html#multicurve>
* The future of LIBOR: Quantitative perspective on benchmarks, overnight, fallback and regulation. <https://murisq.blogspot.com/p/training.html#libor-future>
* Algorithmic Differentiation in Finance. <https://murisq.blogspot.com/p/training.html#ad>
* Central clearing and bilateral margin. <https://murisq.blogspot.com/p/training.html#margin>

Some recent public courses:
* Workshop Multi-curve and collateral framework. One day workshop at The 10th Fixed Income Conference (Barcelona, Spain), September 2014.
* Collateral, regulation and multi-curve. Belfius Financial Engineering Fund Workshop at KUL/Leuven University (Leuven, Belgium), December 2017.
* Workshop *Interest Rate Modelling in the Multi-curve Framework: Collateral and Regulatory Requirements*. LFS Workshop (London, UK), September 2018.
* Workshop *The future of LIBOR: Quantitative perspective on benchmarks, overnight, fallback and regulation*. Finans Foreningen workshop (Copenhagen, Denmark), 24 January 2019.
* Planned: *Interest Rate Modelling in the Multi-curve Framework: Collateral and Regulatory Requirements*. LFS Workshop (New York, USA), 25-26 March 2019.
* Planned: *Interest Rate Modelling in the Multi-curve Framework: Collateral and Regulatory Requirements*. LFS Workshop (Singapore), 3-4 April 2019.

## Advisory

* Developments
    * *Multi-curve and collateral framework*. Collateral discounting, impact of CSA, multi-curve calibration, new benchmarks, cheapest-to-deliver
    * *Interest rate models*: Term structure models, smile, negative rates, stochastic spreads.
    * *Exchange traded instruments*: Development of exchanged traded instruments, detailed term sheet, regulatory approval, CCP's risk management procedures.
    * *Margin methodologies*: Variation and Initial Margin methodologies design. Review and implementation of methodologies used by CCPs (replication). Bilateral margin methodologies.
    * *Simulation*: Model implementation for efficient simulation, xVA underlying models 
    * *Benchmarks*: valuation of instruments indexed on new benchmarks, benchmarks discontinuation, LIBOR fallback analysis and solutions, overnight benchmarks (RFR) transition, valuation impacts, risk management, ALM
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
    * *Bilateral margins*: Quantitative impacts of uncleared margin regulation (UMR), bilateral margin methodologies, ISDA SIMM™ computations.
    * *Compression*: Exposure reduction, portfolio compression
    * *Business strategy*: cleared v uncleared OTC derivatives, cost of trading, access to market infrastructure
    * *Regulatory consultative documents*: Comments on consultative documents.
    * *Negotiation*: Negotiations for efficient access to markets
    
