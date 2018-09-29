# RisQ-ir-models by Marc Henrard

You can find more information about me:
* Profile: [Marc Henrard | LinkedIn](https://www.linkedin.com/in/marchenrard/)
* Papers in finance: [Marc Henrard | SSRN](http://ssrn.com/author=352726)
* Blog: [Multi-curve framework](http://multi-curve-framework.blogspot.com)
* Consulting: [muRisQ Advisory](http://murisq.com/)

This repository proposes code for pricing and risk management of interest rate derivatives.

The models implemented are based on personal research and academic literature as described in each pricer.

RisQ stands for Risk management by Quantitative methods. The term risk management has to be understood as the actual management of risk, which goes beyond its measurement.

Comments and suggestions for improvements are welcomed.

## Foundations

The pricers proposed in this repository are based on OpenGamma Strata (version 1.7.0) library:
http://strata.opengamma.io/

## Contributions

If you are interested in collaborating in research on interest rate modeling and market infrastructure, I would be keen to hear about it. I would also happily published join work in this repository with full credit. 

## Products

### 1. Compounded overnight futures
Description of the futures.
### 2. OIS futures
Description of the futures. Instrument description in: Henrard, M., Risk-Based Overnight-Linked Futures Design (August 22, 2018). Available at SSRN: (https://ssrn.com/abstract=3238640)

## Models

### 1. Bachelier Formula

#### Description
Explicit formula for implicit volatility.

#### References
* Le Floc'h, F. (2016). Fast and Accurate Analytic Basis Point Volatility. Working paper.

### 2. Hull-White one-factor
* Convexity adjustment for futures. Implementation with flexible start and end date.
* Pricing of compounded overnight futures. Reference: Henrard, Marc. (2018) Overnight based futures: convexity adjustment estimation

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


## Professional services

The model proposed here are only a small part of the code I developed or have access to for research and advisory services purposes. Don’t hesitate to contact me if you are interested by other models, require advisory services or are looking for a training around similar model.

### Training

Recent *trainings* in interest rate modelling and risk management include:
* Multi-curve and collateral framework: previous course description at http://multi-curve-framework.blogspot.co.uk/2014/06/course-on-multi-curve-and-collateral.html
* New margin paradigm: changes in market infrastructure, previous course description at http://multi-curve-framework.blogspot.co.uk/2016/03/workshop-on-margin.html
* Algorithmic Differentiation in Finance: course description at http://multi-curve-framework.blogspot.co.uk/2017/10/algorithmic-differentiation-training.html and code repository at https://github.com/marc-henrard/algorithmic-differentiation-book
* Standard Initial Margin Model: a detailed description and implementation
* Collateral, regulation and multi-curve. Belfius Financial Engineering Fund Workshop at KUL/Leuven University (Leuven, Belgium), December 2017.

### Advisory

* Developments
	* Multi-curve and collateral framework.
	* Interest rate models: Term structure models, smile, negative rates, stochastic spreads.
	* Exchange traded instruments: Design of exchanged traded instruments, detailed term sheet, risk management.
	* Margin methodologies: Variation and Initial Margin methodologies design. Review and implementation of methodologies used by CCPs (replication). Bilateral margin methodologies.
	* Simulation: Model implementation for efficient simulation, xVA underlying models
    * LIBOR fallback analysis
* Risk management
	* Hedging strategies (design and back testing)
	* Value-at-Risk
	* Initial Margin models
* Model validation
	* Flow instruments: Multi-curve framework, collateral impact, CSA review.
	* Term structure: Multi-factors models; stochastic spreads.
	* VaR: Parametric, historical, Monte Carlo.
	* Smile: Swaption, cap/floor, negative rates, extrapolation.
	* White paper: Independent assessment of new products and services.
