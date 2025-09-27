import copy
from typing import Optional


class BaseExc(Exception):
    """

    BaseExc是xingchen-utils最基础的异常类，在使用时尽可能使用继承方式进行集成，方便对于不同类型的异常进行捕获

    例如:
    ```python

    from xingchen_utils.exceptions import BaseExc

    class AgentLLMParserExc(BaseExc):
        pass

    ```

    """

    def __init__(
        self, c: int, m: str, oc: int = 0, om: str = "", on: str = "", **kwargs: dict
    ):
        """
        c : code
        m : message
        oc : origin_code
        om : origin_message
        on : origin_name

        c代表当前系统想要抛出的错误码
        m代表当前系统想要抛出的错误码描述信息
        o_c代表当前系统调用其他系统，其他系统抛出的错误码
        o_m代表当前系统调用其他系统，其他系统抛出的错误码信息
        o_n代表当前系统调用其他系统，其他系统名称

        正常使用只需要使用 c 和 m 即可
        当需要对错误码进行映射管理的时候，可以使用 o_c o_m o_n
        """

        self.c = c
        self.m = m
        self.oc = oc
        self.om = om
        self.on = on
        self.kwargs = kwargs

    def __call__(
        self,
        am: str = "",
        *,
        c: Optional[int] = None,
        m: str = "",
        oc: Optional[int] = None,
        om: str = "",
        on: str = "",
        **kwargs: dict,
    ) -> "BaseExc":
        """

        此方法会copy出一个新的异常对象便于修改，避免更改全局声明的异常

        am : append_message
        其他参数同 __init__

        当传递am时，会追加到异常声明时的m
        当传递其他和 __init__ 相同参数时，会覆盖copy出新对象的对应参数

        使用示例：
        ```python

        from xingchen_utils.exceptions import BaseExc

        class AgentLLMParserExc(BaseExc):
            pass

        err1 = AgentLLMParserExc(40021, "LLM结果解析异常")

        raise err1("大模型推理格式不正确")


        ```

        """

        n_s = copy.deepcopy(self)
        if c is not None:
            n_s.c = self.c
        if m:
            n_s.m = self.m
        if oc is not None:
            n_s.oc = self.oc
        if om:
            n_s.om = self.om
        if on:
            n_s.on = self.on

        if am:
            n_s.m = f"{n_s.m},{am}" if n_s.m else am

        n_s.kwargs = kwargs
        return n_s

    def __repr__(self) -> str:
        return f"{self.c}: {self.m}"

    def __str__(self) -> str:
        return self.__repr__()
