<#--
  Impilo "Try another way" — the credential chooser reached from any step-up screen.

  Structurally the stock keycloak.v2 list, with the same "Continuing as" block the password
  and one-time-code steps use, so choosing a different second factor stays visibly inside
  the same sign-in rather than looking like a new one.

  The list itself is Keycloak's: each entry is a real authentication execution the person
  actually holds, submitted as authenticationExecution. Nothing here decides what is on
  offer — that comes from the credentials on the account and the authentication flow.
-->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=false; section>
<!-- template: select-authenticator.ftl (impilo) -->

    <#if section = "header" || section = "show-username">
        <#if section = "header">
            ${msg("loginChooseAuthenticator")}
        </#if>
    <#elseif section = "form">

    <#if auth?has_content && auth.showUsername() && !auth.showResetCredentials()>
        <div class="impilo-continue-as" id="impilo-continue-as">
            <span class="impilo-continue-as__label">${msg("impiloContinuingAs")}</span>
            <span class="impilo-continue-as__identity">${auth.attemptedUsername}</span>
            <a class="impilo-continue-as__switch" href="${url.loginRestartFlowUrl}">${msg("impiloNotYou")}</a>
        </div>
    </#if>
    <p class="impilo-step-hint">${msg("impiloChooseAuthenticatorHint")}</p>

    <ul class="${properties.kcSelectAuthListClass!}" role="list">
        <#list auth.authenticationSelections as authenticationSelection>
            <li class="${properties.kcSelectAuthListItemWrapperClass!}">
                <form id="kc-select-credential-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
                    <input type="hidden" name="authenticationExecution" value="${authenticationSelection.authExecId}">
                </form>
                <div role="button" class="${properties.kcSelectAuthListItemClass!}" onclick="document.forms[${authenticationSelection?index}].requestSubmit()" tabindex="0">
                    <div class="pf-v5-c-data-list__item-content pf-v5-u-flex-wrap-wrap">
                        <div class="${properties.kcSelectAuthListItemIconClass!}">
                            <i class="${properties['${authenticationSelection.iconCssClass}']!authenticationSelection.iconCssClass} ${properties.kcSelectAuthListItemIconPropertyClass!}"></i>
                        </div>
                        <div class="${properties.kcSelectAuthListItemBodyClass!}">
                            <h2 class="${properties.kcSelectAuthListItemHeadingClass!}">
                                ${msg('${authenticationSelection.displayName}')}
                            </h2>
                        </div>
                        <div class="${properties.kcSelectAuthListItemDescriptionClass!}">
                            ${msg('${authenticationSelection.helpText}')}
                        </div>
                    </div>
                    <div class="${properties.kcSelectAuthListItemFillClass!}">
                        <i class="${properties.kcSelectAuthListItemArrowIconClass!}" aria-hidden="true"></i>
                    </div>
                </div>
            </li>
        </#list>
    </ul>

    </#if>
</@layout.registrationLayout>
