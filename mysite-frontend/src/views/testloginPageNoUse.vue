<template>
  <div class="all">
    <!-- 左侧：登录区域 -->
    <div class="log">
      <div class="register">
        <form @submit.prevent="denglu">
          <div class="register-top-grid">
            <h3>用户登录</h3>
            <div class="input">
              <span>用户名 <label style="color: red">*</label></span>
              <input
                type="text"
                v-model="loginForm.name"
                placeholder="请输入用户名"
                class="input-text"
              />
            </div>
            <div class="input">
              <span>密码 <label style="color: red">*</label></span>
              <input
                type="password"
                v-model="loginForm.password"
                placeholder="请输入密码"
                class="input-text"
              />
            </div>
            <div class="text-center">
              <button type="submit" class="tijiao">提交</button>
            </div>
          </div>
        </form>
      </div>
    </div>

    <!-- 右侧：注册引导 -->
    <div class="reg">
      <div class="reg_1">
        <h2>没有账号？</h2>
        <p>立即注册加入我们吧，和我们一起开启旅程吧</p>
        <router-link to="/register">
          <button type="button" class="sig">注册</button>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue';
// 使用 Composition API + TypeScript

// 定义登录表单的数据类型
interface LoginForm {
  name: string;
  password: string;
}

// 定义响应式数据
const loginForm = reactive<LoginForm>({
  name: '',
  password: '',
});

// 登录方法
const denglu = (): void => {
  console.log('登录信息:', loginForm);

  // 这里可以添加登录逻辑，比如调用 API
  if (!loginForm.name || !loginForm.password) {
    alert('请填写用户名和密码');
    return;
  }

  // 模拟登录请求
  // await loginApi(loginForm);
};
</script>

<style scoped>
/* 这里是你原来的 <style> 标签里的全部内容，直接粘贴进来即可 */
.all {
  width: 800px;
  box-shadow: -10px 10px 25px rgba(210, 210, 210, 0.9);
  margin: auto;
  margin-top: 5%;
  display: flex;
  border-radius: 35px;
  background-color: #ffffff;
  height: 550px;
}
.log {
  width: 50%;
  margin: auto;
}
.reg {
  width: 50%;
  height: 100%;
  margin: auto;
  background-color: #20b2aa;
  border-radius: 35px;
  color: #ffffff;
}
.reg_1 {
  text-align: center;
  margin: auto;
  margin-top: 50%;
}
.reg_1 h2 {
  font-weight: 700;
}
.reg_1 p {
  margin: 15px 0px 25px 0px;
}
.sig {
  width: 70px;
  height: 30px;
  border-radius: 12px;
  background-color: #20b2aa;
  border-color: #fff;
  color: #ffffff;
}
#tiao {
  padding: 0em 0;
}
.reg_1 a {
  color: #ffffff;
}
h3 {
  font-size: 3em;
  color: black;
  padding-bottom: 1em;
  margin: 0;
  text-align: center;
  font-family: "Marvel-Regular";
}
.input {
  margin: 10px 50px;
  width: 300px;
  height: 70px;
}
span {
  color: #999;
  font-size: 0.85em;
  padding-bottom: 0.2em;
  display: block;
  text-transform: uppercase;
  margin-bottom: 4px;
}
.input-text {
  border: 1px solid #555;
  outline-color: #fd9f3e;
  width: 90%;
  font-size: 1em;
  padding: 0.5em;
  line-height: inherit;
}
.register-top-grid {
  color: black;
  padding-bottom: 1em;
  margin: 0;
  font-family: "Marvel-Regular";
  margin: 10px 0;
}
.text-center {
  text-align: center;
}
.tijiao {
  color: rgb(255, 253, 253);
  width: 80px;
  height: 35px;
  background-color: rgb(241, 52, 10);
  border: none;
}
</style>
