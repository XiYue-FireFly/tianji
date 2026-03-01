package com.tianji.learning.mq;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LessonChangeListener {

    private final ILearningLessonService learningLessonService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.pay.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY
    ))
    public void listenLessonPay(OrderBasicDTO order){
        //增加代码的健壮性
        if (order == null || order.getOrderId() == null || CollUtils.isEmpty(order.getCourseIds())){
            log.error("接受到的mq消息有误，订单参数为空");
            return;
        }
        //添加课程
        log.debug("接受用户{}的订单支付消息，开始添加课程{}", order.getUserId(), order.getCourseIds());
        learningLessonService.addUserLessons(order.getUserId(),order.getCourseIds());

    }
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.cancel.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_REFUND_KEY
    ))
    public void listenLessonCancel(OrderBasicDTO order){
        //增加代码的健壮性
        if (order == null ||  CollUtils.isEmpty(order.getCourseIds()) || order.getUserId() == null){
            log.error("接受到的mq消息有误，订单参数为空");
            return;
        }
        //删除课程
        log.debug("接受用户{}的订单取消消息，开始删除课程{}", order.getUserId(), order.getCourseIds());
        order.getCourseIds().forEach(courseId->{
            log.info("接受到订单取消消息，开始删除课程{}从学习表", courseId);
            learningLessonService.deleteUserLessons(order.getUserId(), courseId);
        });

    }
}
