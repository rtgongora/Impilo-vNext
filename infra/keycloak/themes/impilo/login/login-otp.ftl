<#--
  Impilo one-time-code step — same door, one step further along.

  Two problems with the stock keycloak.v2 page:

  1. Its header is ${msg("doLogIn")}, the SAME string as the submit button. With our
     doLogIn="Continue securely" the screen read "Continue securely / … / Continue securely"
     and gave no clue what was being asked for.
  2. It renders the carried-over identity through template.ftl's kcUsername macro as a
     READONLY TEXT INPUT beside a "Restart login" icon button — a different shape from the
     password step, where the same fact is shown as "Continuing as <identifier>". Two
     consecutive steps of one journey looked like two unrelated screens.

  This template states what is being asked for, and renders identity in the SAME
  "Continuing as" block the password step uses. The stock username input is suppressed in
  CSS (impilo.css, :has() on the group containing #kc-attempted-username) rather than by
  forking template.ftl, which is 200+ lines and moves with every Keycloak release. If a
  browser lacks :has() the old block reappears — visually redundant, never broken.
-->
<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('totp'); section>
<!-- template: login-otp.ftl (impilo) -->

    <#if section="header">
        ${msg("impiloOtpTitle")}
    <#elseif section="form">
        <#if auth?has_content && auth.showUsername() && !auth.showResetCredentials()>
            <div class="impilo-continue-as" id="impilo-continue-as">
                <span class="impilo-continue-as__label">${msg("impiloContinuingAs")}</span>
                <span class="impilo-continue-as__identity">${auth.attemptedUsername}</span>
                <a class="impilo-continue-as__switch" href="${url.loginRestartFlowUrl}">${msg("impiloNotYou")}</a>
            </div>
        </#if>
        <p class="impilo-step-hint">${msg("impiloOtpHint")}</p>
        <form id="kc-otp-login-form" class="${properties.kcFormClass!}" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
            <input id="selectedCredentialId" type="hidden" name="selectedCredentialId" value="${otpLogin.selectedCredentialId!''}">
            <#if otpLogin.userOtpCredentials?size gt 1>
                <div class="${properties.kcFormGroupClass!}">
                    <div class="${properties.kcInputWrapperClass!}">
                        <#list otpLogin.userOtpCredentials as otpCredential>
                            <div id="kc-otp-credential-${otpCredential?index}" class="${properties.kcLoginOTPListClass!}"
                                    onclick="toggleOTP(${otpCredential?index}, '${otpCredential.id}')">
                                <span class="${properties.kcLoginOTPListItemHeaderClass!}">
                                    <span class="${properties.kcLoginOTPListItemIconBodyClass!}">
                                      <i class="${properties.kcLoginOTPListItemIconClass!}" aria-hidden="true"></i>
                                    </span>
                                    <span class="${properties.kcLoginOTPListItemTitleClass!}">${otpCredential.userLabel}</span>
                                </span>
                            </div>
                        </#list>
                    </div>
                </div>
            </#if>

            <@field.input name="otp" label=msg("loginOtpOneTime") autocomplete="one-time-code" fieldName="totp" autofocus=true />

            <@buttons.loginButton />
        </form>
        <script>
            <#outputformat "JavaScript">
            function toggleOTP(index, value) {
                document.getElementById("selectedCredentialId").value = value;
                Array.from(document.getElementsByClassName(${properties.kcLoginOTPListSelectedClass!?c})).map(i => i.classList.remove(${properties.kcLoginOTPListSelectedClass!?c}));
                document.getElementById("kc-otp-credential-" + index).classList.add(${properties.kcLoginOTPListSelectedClass!?c});
            }
            </#outputformat>
        </script>
    </#if>
</@layout.registrationLayout>
