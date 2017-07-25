# RisQ-ir-models by Marc Henrard

This repository proposes code for pricing and risk management of interest rate derivatives.

The models implemented are based on personal research and academic literature as described in each pricer.

RisQ stands for Risk management by Quantitative methods. The term risk management has to be understood as the actual management of risk, which goes beyond its measurement.

Comments and suggestions for improvements are welcomed.

## Foundations

The pricers proposed in this repository are based on OpenGamma Strata (version 1.3.0) library:
http://strata.opengamma.io/

## Contributions

If you are interested in collaborating in research on interest rate modeling and market infrastructure, I would be keen to hear about it. I would also happily published join work in this repository with full credit. 

## Models

### 1. Bachelier Formula

#### Description
Explicit formula for implicit volatility.

#### References
* Le Floc'€™h, F (2016). Fast and Accurate Analytic Basis Point Volatility. Working paper.

### 2. Rational Model

#### Description
The model describes the evolution of the discount curve and the Libor process in an explicit way. The quantities are not presented through a SDE but directly as explicit quantities depending on simple random variables. This leads to explicit dynamic and in general (almost) explicit formulas for simple instruments and very easy Monte Carlo simulations.

#### References
* Crepey, S., Macrina, A., Nguyen, T. M., and Skovmand, D. (2016). Rational multi-curve models with counterparty-risk valuation adjustments. *Quantitative Finance* , 16(6): 847-866.
* Macrina, A. (2014). Heat kernel models for asset pricing. *International Journal of Theoretical and Applied Finance*, 17(7).
* Henrard, M. (2017). Rational multi-curve interest rate model: pricing of liquid market instruments. Working paper.

#### Code 
* Vanilla swaptions with several versions of the model and several numerical approaches (explicit formulas, numerical integration, Monte Carlo).


## Professional services

The model proposed here are only a small part of the code I developed or have access to for research and advisory services purposes. Donâ€™t hesitate to contact me if you are interested by other models, require advisory services or are looking for a training around similar model.

### Training

Recent *trainings* in interest rate modeling and risk management include:
* Multi-curve and collateral framework (http://multi-curve-framework.blogspot.co.uk/2014/06/course-on-multi-curve-and-collateral.html)
* New margin paradigm: changes in market infrastructure (http://multi-curve-framework.blogspot.co.uk/2016/03/workshop-on-margin.html)
* Algorithmic Differentiation in Finance (see the repository https://github.com/marc-henrard/algorithmic-differentiation-book)
* Standard Initial Margin Model: a detailed description and implementation

### Advisory
